//@formatter:off
/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nmorel.gwtjackson.client.stream.impl;

import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.nmorel.gwtjackson.client.arrays.FastArrayInteger;
import com.github.nmorel.gwtjackson.client.stream.JsonToken;

/**
 * Abstract base class for JSON readers that implements shared parsing logic.
 * Subclasses provide the character-access strategy (buffered vs direct string).
 *
 * @author Jesse Wilson
 * @author Nicolas Morel
 */
public abstract class AbstractJsonReader implements com.github.nmorel.gwtjackson.client.stream.JsonReader
{
  protected static final Logger logger = Logger.getLogger( "JsonReader" );

  protected static final char[] NON_EXECUTE_PREFIX = ")]}'\n".toCharArray();
  protected static final long MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10;

  protected final static long MIN_INT_L = (long) Integer.MIN_VALUE;
  protected final static long MAX_INT_L = (long) Integer.MAX_VALUE;

  protected final static BigInteger MIN_LONG_BIGINTEGER = new BigInteger("" + Long.MIN_VALUE);
  protected final static BigInteger MAX_LONG_BIGINTEGER = new BigInteger("" + Long.MAX_VALUE);

  protected static final int PEEKED_NONE = 0;
  protected static final int PEEKED_BEGIN_OBJECT = 1;
  protected static final int PEEKED_END_OBJECT = 2;
  protected static final int PEEKED_BEGIN_ARRAY = 3;
  protected static final int PEEKED_END_ARRAY = 4;
  protected static final int PEEKED_TRUE = 5;
  protected static final int PEEKED_FALSE = 6;
  protected static final int PEEKED_NULL = 7;
  protected static final int PEEKED_SINGLE_QUOTED = 8;
  protected static final int PEEKED_DOUBLE_QUOTED = 9;
  protected static final int PEEKED_UNQUOTED = 10;
  /** When this is returned, the string value is stored in peekedString. */
  protected static final int PEEKED_BUFFERED = 11;
  protected static final int PEEKED_SINGLE_QUOTED_NAME = 12;
  protected static final int PEEKED_DOUBLE_QUOTED_NAME = 13;
  protected static final int PEEKED_UNQUOTED_NAME = 14;
  /** When this is returned, the integer value is stored in peekedLong (DefaultJsonReader only). */
  protected static final int PEEKED_LONG = 15;
  protected static final int PEEKED_NUMBER = 16;
  protected static final int PEEKED_EOF = 17;

  /* State machine when parsing numbers */
  protected static final int NUMBER_CHAR_NONE = 0;
  protected static final int NUMBER_CHAR_SIGN = 1;
  protected static final int NUMBER_CHAR_DIGIT = 2;
  protected static final int NUMBER_CHAR_DECIMAL = 3;
  protected static final int NUMBER_CHAR_FRACTION_DIGIT = 4;
  protected static final int NUMBER_CHAR_EXP_E = 5;
  protected static final int NUMBER_CHAR_EXP_SIGN = 6;
  protected static final int NUMBER_CHAR_EXP_DIGIT = 7;

  /** True to accept non-spec compliant JSON */
  protected boolean lenient = false;

  protected int pos = 0;
  protected int limit = 0;

  protected int lineNumber = 0;
  protected int lineStart = 0;

  protected int peeked = PEEKED_NONE;

  /**
   * The number of characters in a peeked number literal. Increment 'pos' by
   * this after reading a number.
   */
  protected int peekedNumberLength;

  /**
   * A peeked string that should be parsed on the next double, long or string.
   * This is populated before a numeric value is parsed and used if that parsing
   * fails.
   */
  protected String peekedString;

  /*
   * The nesting stack. Using a manual array rather than an ArrayList saves 20%.
   */
  protected FastArrayInteger stack = new FastArrayInteger();
  protected int stackSize = 0;
  {
    stack.set(stackSize++, JsonScope.EMPTY_DOCUMENT);
  }

  // ---- Abstract methods that subclasses must implement ----

  /** Read character at the given position. */
  protected abstract char charAt(int pos);

  /** Ensure at least {@code minimum} characters are available starting from {@code pos}. */
  protected abstract boolean ensureAvailable(int minimum);

  /** Extract a string from the input starting at {@code start} with given {@code length}. */
  protected abstract String extractString(int start, int length);

  /** Get the raw input string. */
  protected abstract String getInputString();

  /** Read the next non-whitespace character, handling comments. */
  protected abstract int nextNonWhitespace(boolean throwOnEof);

  /** Read a quoted string value. */
  protected abstract String nextQuotedValue(char quote);

  /** Read an unquoted string value. */
  protected abstract String nextUnquotedValue();

  /** Skip a quoted string value. */
  protected abstract void skipQuotedValue(char quote);

  /** Skip an unquoted string value. */
  protected abstract void skipUnquotedValue();

  /** Read an escape character sequence. */
  protected abstract char readEscapeCharacter();

  /** Skip to end of current line. */
  protected abstract void skipToEndOfLine();

  /** Skip forward to the given string. */
  protected abstract boolean skipTo(String toFind);

  /** Consume the non-execute prefix if present. */
  protected abstract void consumeNonExecutePrefix();

  /** Peek at the next number token. */
  protected abstract int peekNumber();

  // ---- Shared concrete methods ----

  /** {@inheritDoc} */
  @Override
  public final void setLenient( boolean lenient ) {
    this.lenient = lenient;
  }

  /**
   * Returns true if this parser is liberal in what it accepts.
   *
   * @return a boolean.
   */
  public final boolean isLenient() {
    return lenient;
  }

  /** {@inheritDoc} */
  @Override
  public void beginArray()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_ARRAY) {
      push(JsonScope.EMPTY_ARRAY);
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected BEGIN_ARRAY but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void endArray()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_ARRAY) {
      stackSize--;
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected END_ARRAY but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void beginObject()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_OBJECT) {
      push(JsonScope.EMPTY_OBJECT);
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected BEGIN_OBJECT but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void endObject()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_OBJECT) {
      stackSize--;
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected END_OBJECT but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasNext()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY;
  }

  /** {@inheritDoc} */
  @Override
  public JsonToken peek()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    switch (p) {
    case PEEKED_BEGIN_OBJECT:
      return JsonToken.BEGIN_OBJECT;
    case PEEKED_END_OBJECT:
      return JsonToken.END_OBJECT;
    case PEEKED_BEGIN_ARRAY:
      return JsonToken.BEGIN_ARRAY;
    case PEEKED_END_ARRAY:
      return JsonToken.END_ARRAY;
    case PEEKED_SINGLE_QUOTED_NAME:
    case PEEKED_DOUBLE_QUOTED_NAME:
    case PEEKED_UNQUOTED_NAME:
      return JsonToken.NAME;
    case PEEKED_TRUE:
    case PEEKED_FALSE:
      return JsonToken.BOOLEAN;
    case PEEKED_NULL:
      return JsonToken.NULL;
    case PEEKED_SINGLE_QUOTED:
    case PEEKED_DOUBLE_QUOTED:
    case PEEKED_UNQUOTED:
    case PEEKED_BUFFERED:
      return JsonToken.STRING;
    case PEEKED_LONG:
    case PEEKED_NUMBER:
      return JsonToken.NUMBER;
    case PEEKED_EOF:
      return JsonToken.END_DOCUMENT;
    default:
      throw new AssertionError();
    }
  }

  protected int doPeek()
  {
    int peekStack = stack.get(stackSize - 1);
    if (peekStack == JsonScope.EMPTY_ARRAY) {
      stack.set(stackSize - 1, JsonScope.NONEMPTY_ARRAY);
    } else if (peekStack == JsonScope.NONEMPTY_ARRAY) {
      // Look for a comma before the next element.
      int c = nextNonWhitespace(true);
      switch (c) {
      case ']':
        return peeked = PEEKED_END_ARRAY;
      case ';':
        checkLenient(); // fall-through
      case ',':
        break;
      default:
        throw syntaxError("Unterminated array");
      }
    } else if (peekStack == JsonScope.EMPTY_OBJECT || peekStack == JsonScope.NONEMPTY_OBJECT) {
      stack.set(stackSize - 1, JsonScope.DANGLING_NAME);
      // Look for a comma before the next element.
      if (peekStack == JsonScope.NONEMPTY_OBJECT) {
        int c = nextNonWhitespace(true);
        switch (c) {
        case '}':
          return peeked = PEEKED_END_OBJECT;
        case ';':
          checkLenient(); // fall-through
        case ',':
          break;
        default:
          throw syntaxError("Unterminated object");
        }
      }
      int c = nextNonWhitespace(true);
      switch (c) {
      case '"':
        return peeked = PEEKED_DOUBLE_QUOTED_NAME;
      case '\'':
        checkLenient();
        return peeked = PEEKED_SINGLE_QUOTED_NAME;
      case '}':
        if (peekStack != JsonScope.NONEMPTY_OBJECT) {
          return peeked = PEEKED_END_OBJECT;
        } else {
          throw syntaxError("Expected name");
        }
      default:
        checkLenient();
        pos--; // Don't consume the first character in an unquoted string.
        if (isLiteral((char) c)) {
          return peeked = PEEKED_UNQUOTED_NAME;
        } else {
          throw syntaxError("Expected name");
        }
      }
    } else if (peekStack == JsonScope.DANGLING_NAME) {
      stack.set(stackSize - 1, JsonScope.NONEMPTY_OBJECT);
      // Look for a colon before the value.
      int c = nextNonWhitespace(true);
      switch (c) {
      case ':':
        break;
      case '=':
        checkLenient();
        if (ensureAvailable(1) && charAt(pos) == '>') {
          pos++;
        }
        break;
      default:
        throw syntaxError("Expected ':'");
      }
    } else if (peekStack == JsonScope.EMPTY_DOCUMENT) {
      if (lenient) {
        consumeNonExecutePrefix();
      }
      stack.set(stackSize - 1, JsonScope.NONEMPTY_DOCUMENT);
    } else if (peekStack == JsonScope.NONEMPTY_DOCUMENT) {
      int c = nextNonWhitespace(false);
      if (c == -1) {
        return peeked = PEEKED_EOF;
      } else {
        checkLenient();
        pos--;
      }
    } else if (peekStack == JsonScope.CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    }

    int c = nextNonWhitespace(true);
    switch (c) {
    case ']':
      if (peekStack == JsonScope.EMPTY_ARRAY) {
        return peeked = PEEKED_END_ARRAY;
      }
      // fall-through to handle ",]"
    case ';':
    case ',':
      // In lenient mode, a 0-length literal in an array means 'null'.
      if (peekStack == JsonScope.EMPTY_ARRAY || peekStack == JsonScope.NONEMPTY_ARRAY) {
        checkLenient();
        pos--;
        return peeked = PEEKED_NULL;
      } else {
        throw syntaxError("Unexpected value");
      }
    case '\'':
      checkLenient();
      return peeked = PEEKED_SINGLE_QUOTED;
    case '"':
      if (stackSize == 1) {
        checkLenient();
      }
      return peeked = PEEKED_DOUBLE_QUOTED;
    case '[':
      return peeked = PEEKED_BEGIN_ARRAY;
    case '{':
      return peeked = PEEKED_BEGIN_OBJECT;
    default:
      pos--; // Don't consume the first character in a literal value.
    }

    if (stackSize == 1) {
      checkLenient(); // Top-level value isn't an array or an object.
    }

    int result = peekKeyword();
    if (result != PEEKED_NONE) {
      return result;
    }

    result = peekNumber();
    if (result != PEEKED_NONE) {
      return result;
    }

    if (!isLiteral(charAt(pos))) {
      throw syntaxError("Expected value");
    }

    checkLenient();
    return peeked = PEEKED_UNQUOTED;
  }

  protected int peekKeyword()
  {
    // Figure out which keyword we're matching against by its first character.
    char c = charAt(pos);
    String keyword;
    String keywordUpper;
    int peeking;
    if (c == 't' || c == 'T') {
      keyword = "true";
      keywordUpper = "TRUE";
      peeking = PEEKED_TRUE;
    } else if (c == 'f' || c == 'F') {
      keyword = "false";
      keywordUpper = "FALSE";
      peeking = PEEKED_FALSE;
    } else if (c == 'n' || c == 'N') {
      keyword = "null";
      keywordUpper = "NULL";
      peeking = PEEKED_NULL;
    } else {
      return PEEKED_NONE;
    }

    // Confirm that chars [1..length) match the keyword.
    int length = keyword.length();
    for (int i = 1; i < length; i++) {
      if (!ensureAvailable(i + 1)) {
        return PEEKED_NONE;
      }
      c = charAt(pos + i);
      if (c != keyword.charAt(i) && c != keywordUpper.charAt(i)) {
        return PEEKED_NONE;
      }
    }

    if (ensureAvailable(length + 1) && isLiteral(charAt(pos + length))) {
      return PEEKED_NONE; // Don't match trues, falsey or nullsoft!
    }

    // We've found the keyword followed either by EOF or by a non-literal character.
    pos += length;
    return peeked = peeking;
  }

  protected boolean isLiteral(char c)
  {
    switch (c) {
    case '/':
    case '\\':
    case ';':
    case '#':
    case '=':
      checkLenient(); // fall-through
    case '{':
    case '}':
    case '[':
    case ']':
    case ':':
    case ',':
    case ' ':
    case '\t':
    case '\f':
    case '\r':
    case '\n':
      return false;
    default:
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String nextName()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED_NAME) {
      result = nextUnquotedValue();
    } else if (p == PEEKED_SINGLE_QUOTED_NAME) {
      result = nextQuotedValue('\'');
    } else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
      result = nextQuotedValue('"');
    } else {
      throw new IllegalStateException("Expected a name but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peeked = PEEKED_NONE;
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean nextBoolean()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_TRUE) {
      peeked = PEEKED_NONE;
      return true;
    } else if (p == PEEKED_FALSE) {
      peeked = PEEKED_NONE;
      return false;
    }
    throw new IllegalStateException("Expected a boolean but was " + peek()
        + " at line " + getLineNumber() + " column " + getColumnNumber());
  }

  /** {@inheritDoc} */
  @Override
  public void nextNull()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_NULL) {
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected null but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    peeked = PEEKED_NONE;
    stack.set(0, JsonScope.CLOSED);
    stackSize = 1;
  }

  /** {@inheritDoc} */
  @Override
  public void skipValue()
  {
    int count = 0;
    do {
      int p = peeked;
      if (p == PEEKED_NONE) {
        p = doPeek();
      }

      if (p == PEEKED_BEGIN_ARRAY) {
        push(JsonScope.EMPTY_ARRAY);
        count++;
      } else if (p == PEEKED_BEGIN_OBJECT) {
        push(JsonScope.EMPTY_OBJECT);
        count++;
      } else if (p == PEEKED_END_ARRAY) {
        stackSize--;
        count--;
      } else if (p == PEEKED_END_OBJECT) {
        stackSize--;
        count--;
      } else if (p == PEEKED_UNQUOTED_NAME || p == PEEKED_UNQUOTED) {
        skipUnquotedValue();
      } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_SINGLE_QUOTED_NAME) {
        skipQuotedValue('\'');
      } else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_DOUBLE_QUOTED_NAME) {
        skipQuotedValue('"');
      } else if (p == PEEKED_NUMBER) {
        pos += peekedNumberLength;
      }
      peeked = PEEKED_NONE;
    } while (count != 0);
  }

  protected void push(int newTop) {
    stack.set(stackSize++, newTop);
  }

  /** {@inheritDoc} */
  @Override
  public int getLineNumber() {
    return lineNumber + 1;
  }

  /** {@inheritDoc} */
  @Override
  public int getColumnNumber() {
    return pos - lineStart + 1;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonReader at line " + getLineNumber() + " column " + getColumnNumber();
  }

  protected void checkLenient()
  {
    if (!lenient) {
      throw syntaxError("Use JsonReader.setLenient(true) to accept malformed JSON");
    }
  }

  protected MalformedJsonException syntaxError(String message)
  {
    String mess = message + " at line " + getLineNumber() + " column " + getColumnNumber();
    logger.log(Level.SEVERE, mess);
    throw new MalformedJsonException(mess);
  }
}
//@formatter:on
