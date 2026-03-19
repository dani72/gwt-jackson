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

import com.github.nmorel.gwtjackson.client.exception.JsonDeserializationException;
import com.github.nmorel.gwtjackson.client.stream.JsonWriter;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;

/**
 * A JSON reader that operates directly on the full input String without buffering.
 *
 * @author Jesse Wilson
 * @author Nicolas Morel
 * @since 1.6
 * @version $Id: $
 */
public class NonBufferedJsonReader extends AbstractJsonReader
{
  private final static long MAX_SAFE_INTEGER = 9007199254740991L;
  private final static long MIN_SAFE_INTEGER = -9007199254740991L;

  /** The input JSON. */
  private final String in;

  /**
   * Creates a new instance that reads a JSON-encoded stream from {@code in}.
   *
   * @param in a {@link java.lang.String} object.
   */
  public NonBufferedJsonReader( String in ) {
    if (in == null) {
      throw new NullPointerException("in == null");
    }
    this.in = in;
    this.limit = in.length();
    if (limit > 0 && in.charAt( 0 ) == '\ufeff') {
      pos++;
      lineStart++;
    }
  }

  // ---- Abstract method implementations ----

  @Override
  protected char charAt(int pos) {
    return in.charAt(pos);
  }

  @Override
  protected boolean ensureAvailable(int minimum) {
    return pos + minimum <= limit;
  }

  @Override
  protected String extractString(int start, int length) {
    return in.substring(start, start + length);
  }

  @Override
  protected String getInputString() {
    return in;
  }

  /** {@inheritDoc} */
  @Override
  public String getInput(){
    return in;
  }

  @Override
  protected int nextNonWhitespace(boolean throwOnEof)
  {
    /*
     * This code uses ugly local variables 'p' and 'l' representing the 'pos'
     * and 'limit' fields respectively. Using locals rather than fields saves
     * a few field reads for each whitespace character in a pretty-printed
     * document, resulting in a 5% speedup.
     */
    int p = pos;
    while (true) {
      if (p == limit) {
        pos = p;
        break;
      }

      int c = in.charAt(p++);
      if (c == '\n') {
        lineNumber++;
        lineStart = p;
        continue;
      } else if (c == ' ' || c == '\r' || c == '\t') {
        continue;
      }

      if (c == '/') {
        pos = p;
        if (p == limit) {
          return c;
        }

        checkLenient();
        char peek = in.charAt(pos);
        switch (peek) {
        case '*':
          // skip a /* c-style comment */
          pos++;
          if (!skipTo("*/")) {
            throw syntaxError("Unterminated comment");
          }
          p = pos + 2;
          continue;

        case '/':
          // skip a // end-of-line comment
          pos++;
          skipToEndOfLine();
          p = pos;
          continue;

        default:
          return c;
        }
      } else if (c == '#') {
        pos = p;
        /*
         * Skip a # hash end-of-line comment. The JSON RFC doesn't
         * specify this behaviour, but it's required to parse
         * existing documents. See http://b/2571423.
         */
        checkLenient();
        skipToEndOfLine();
        p = pos;
      } else {
        pos = p;
        return c;
      }
    }
    if (throwOnEof) {
      String mess = "End of input at line " + getLineNumber() + " column " + getColumnNumber();
      logger.log(Level.SEVERE, mess);
      throw new JsonDeserializationException(mess);
    } else {
      return -1;
    }
  }

  @Override
  protected String nextQuotedValue(char quote)
  {
    StringBuilder builder = new StringBuilder();
    int p = pos;
    /* the index of the first character not yet appended to the builder. */
    int start = p;
    while (p < limit) {
      int c = in.charAt(p++);

      if (c == quote) {
        pos = p;
        builder.append(in.substring(start, p - 1));
        return builder.toString();
      } else if (c == '\\') {
        pos = p;
        builder.append(in.substring(start, p - 1));
        builder.append(readEscapeCharacter());
        p = pos;
        start = p;
      } else if (c == '\n') {
        lineNumber++;
        lineStart = p;
      }
    }

    throw syntaxError("Unterminated string");
  }

  @SuppressWarnings("fallthrough")
  @Override
  protected String nextUnquotedValue()
  {
    int i = 0;

    findNonLiteralCharacter:
    for (; pos + i < limit; i++) {
      switch (in.charAt(pos + i)) {
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
        break findNonLiteralCharacter;
      }
    }

    String result = in.substring( pos, pos + i);
    pos += i;
    return result;
  }

  @Override
  protected void skipQuotedValue(char quote)
  {
    int p = pos;
    int l = limit;
    while (p < l) {
      int c = in.charAt(p++);
      if (c == quote) {
        pos = p;
        return;
      } else if (c == '\\') {
        pos = p;
        readEscapeCharacter();
        p = pos;
      } else if (c == '\n') {
        lineNumber++;
        lineStart = p;
      }
    }
    throw syntaxError("Unterminated string");
  }

  @Override
  protected void skipUnquotedValue()
  {
    int i = 0;
    for (; pos + i < limit; i++) {
      switch (in.charAt(pos + i)) {
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
        pos += i;
        return;
      }
    }
    pos += i;
  }

  @Override
  protected char readEscapeCharacter()
  {
    if (pos == limit) {
      throw syntaxError("Unterminated escape sequence");
    }

    char escaped = in.charAt(pos++);
    switch (escaped) {
    case 'u':
      if (pos + 4 > limit) {
        throw syntaxError("Unterminated escape sequence");
      }
      // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
      char result = 0;
      for (int i = pos, end = i + 4; i < end; i++) {
        char c = in.charAt(i);
        result <<= 4;
        if (c >= '0' && c <= '9') {
          result += (c - '0');
        } else if (c >= 'a' && c <= 'f') {
          result += (c - 'a' + 10);
        } else if (c >= 'A' && c <= 'F') {
          result += (c - 'A' + 10);
        } else {
          throw new NumberFormatException("\\u" + in.substring(pos, pos + 4));
        }
      }
      pos += 4;
      return result;

    case 't':
      return '\t';

    case 'b':
      return '\b';

    case 'n':
      return '\n';

    case 'r':
      return '\r';

    case 'f':
      return '\f';

    case '\n':
      lineNumber++;
      lineStart = pos;
      // fall-through

    case '\'':
    case '"':
    case '\\':
    default:
      return escaped;
    }
  }

  @Override
  protected void skipToEndOfLine()
  {
    while (pos < limit) {
      char c = in.charAt(pos++);
      if (c == '\n') {
        lineNumber++;
        lineStart = pos;
        break;
      } else if (c == '\r') {
        break;
      }
    }
  }

  @Override
  protected boolean skipTo(String toFind)
  {
    outer:
    for (; pos + toFind.length() <= limit; pos++) {
      if (in.charAt(pos) == '\n') {
        lineNumber++;
        lineStart = pos + 1;
        continue;
      }
      for (int c = 0; c < toFind.length(); c++) {
        if (in.charAt(pos + c) != toFind.charAt(c)) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  protected void consumeNonExecutePrefix()
  {
    // fast forward through the leading whitespace
    nextNonWhitespace(true);
    pos--;

    if (pos + NON_EXECUTE_PREFIX.length > limit) {
      return;
    }

    for (int i = 0; i < NON_EXECUTE_PREFIX.length; i++) {
      if (in.charAt(pos + i) != NON_EXECUTE_PREFIX[i]) {
        return; // not a security token!
      }
    }

    // we consumed a security token!
    pos += NON_EXECUTE_PREFIX.length;
  }

  @Override
  protected int peekNumber()
  {
    int last = NUMBER_CHAR_NONE;

    int i = 0;

    charactersOfNumber:
    for (; true; i++) {
      if (pos + i == limit) {
        break;
      }

      char c = in.charAt(pos + i);
      switch (c) {
      case '-':
        if (last == NUMBER_CHAR_NONE) {
          last = NUMBER_CHAR_SIGN;
          continue;
        } else if (last == NUMBER_CHAR_EXP_E) {
          last = NUMBER_CHAR_EXP_SIGN;
          continue;
        }
        return PEEKED_NONE;

      case '+':
        if (last == NUMBER_CHAR_EXP_E) {
          last = NUMBER_CHAR_EXP_SIGN;
          continue;
        }
        return PEEKED_NONE;

      case 'e':
      case 'E':
        if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
          last = NUMBER_CHAR_EXP_E;
          continue;
        }
        return PEEKED_NONE;

      case '.':
        if (last == NUMBER_CHAR_DIGIT) {
          last = NUMBER_CHAR_DECIMAL;
          continue;
        }
        return PEEKED_NONE;

      default:
        if (c < '0' || c > '9') {
          if (!isLiteral(c)) {
            break charactersOfNumber;
          }
          return PEEKED_NONE;
        }
        if (last == NUMBER_CHAR_SIGN || last == NUMBER_CHAR_NONE) {
          last = NUMBER_CHAR_DIGIT;
        } else if (last == NUMBER_CHAR_NONE && c == '0') {
          // Leading '0' prefix is not allowed (since it could be octal).
          return PEEKED_NONE;
        } else if (last == NUMBER_CHAR_DECIMAL) {
          last = NUMBER_CHAR_FRACTION_DIGIT;
        } else if (last == NUMBER_CHAR_EXP_E || last == NUMBER_CHAR_EXP_SIGN) {
          last = NUMBER_CHAR_EXP_DIGIT;
        }
      }
    }

    // It's a number that's all we should care about at this stage
    if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT
        || last == NUMBER_CHAR_EXP_DIGIT) {
      peekedNumberLength = i;
      return peeked = PEEKED_NUMBER;
    } else {
      return PEEKED_NONE;
    }
  }

  // ---- Number handling (delegates to nextNumber()) ----

  /** {@inheritDoc} */
  @Override
  public String nextString()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED) {
      result = nextUnquotedValue();
    } else if (p == PEEKED_SINGLE_QUOTED) {
      result = nextQuotedValue('\'');
    } else if (p == PEEKED_DOUBLE_QUOTED) {
      result = nextQuotedValue('"');
    } else if (p == PEEKED_BUFFERED) {
      result = peekedString;
      peekedString = null;
    } else if (p == PEEKED_NUMBER) {
      result = in.substring( pos, pos + peekedNumberLength);
      pos += peekedNumberLength;
    } else {
      throw new IllegalStateException("Expected a string but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peeked = PEEKED_NONE;
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public double nextDouble()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_NUMBER) {
      peekedString = in.substring(pos, pos + peekedNumberLength);
      pos += peekedNumberLength;
    } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
    } else if (p == PEEKED_UNQUOTED) {
      peekedString = nextUnquotedValue();
    } else if (p != PEEKED_BUFFERED) {
      throw new IllegalStateException("Expected a double but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    String oldPeeked = peekedString;
    Number asNumber = nextNumber();
    if (asNumber instanceof Long && (asNumber.longValue() > MAX_SAFE_INTEGER || asNumber.longValue() < MIN_SAFE_INTEGER)) {
      // Do not advance the reader on failure
      peeked = PEEKED_BUFFERED;
      peekedString = oldPeeked;
      throw new NumberFormatException("Integer is too big for a double " + asNumber
            + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    return asNumber.doubleValue();
  }

  /** {@inheritDoc} */
  @Override
  public long nextLong()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_NUMBER) {
      peekedString = in.substring( pos, pos + peekedNumberLength);
      pos += peekedNumberLength;
    } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
      try {
        long result = Long.parseLong( peekedString );
        peeked = PEEKED_NONE;
        return result;
      } catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    } else if (p != PEEKED_BUFFERED) {
      throw new IllegalStateException("Expected a long but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    String oldPeeked = peekedString;
    Number asNumber = nextNumber();
    boolean isDoubleALong = (asNumber instanceof Double)
            && Math.rint(asNumber.doubleValue()) == asNumber.doubleValue();

    if (!(asNumber instanceof Long) && !(asNumber instanceof Integer) && !isDoubleALong) {
      // Do not advance the reader on failure
      peeked = PEEKED_BUFFERED;
      peekedString = oldPeeked;
      throw new NumberFormatException("Expected a long but was " + asNumber
              + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    return asNumber.longValue();
  }

  /** {@inheritDoc} */
  @Override
  public int nextInt()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    int result;

    if (p == PEEKED_NUMBER) {
      peekedString = in.substring(pos, pos + peekedNumberLength);
      pos += peekedNumberLength;
    } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
      try {
        result = Integer.parseInt( peekedString );
        peeked = PEEKED_NONE;
        return result;
      } catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    } else if (p != PEEKED_BUFFERED) {
      throw new IllegalStateException("Expected an int but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    String oldPeeked = peekedString;
    Number asNumber = nextNumber();
    boolean isDoubleAnInteger = (asNumber instanceof Double)
            && Math.rint(asNumber.doubleValue()) == asNumber.doubleValue()
            && asNumber.doubleValue() <= MAX_INT_L && asNumber.doubleValue() >= MIN_INT_L;

    if (!(asNumber instanceof Integer) && !isDoubleAnInteger) {
      // Do not advance the reader on failure
      peeked = PEEKED_BUFFERED;
      peekedString = oldPeeked;
      throw new NumberFormatException("Expected an int but was " + asNumber
              + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    return asNumber.intValue();
  }

  /** {@inheritDoc} */
  @Override
  public String nextValue()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if(p == PEEKED_NULL) {
      peeked = PEEKED_NONE;
      return "null";
    }

    // TODO rewrite to avoid using a JsonWriter
    // we should be able to write the tree without escaping/unescaping
    JsonWriter writer = new FastJsonWriter( new StringBuilder() );
    writer.setLenient( true );

    int count = 0;
    do {
      p = peeked;
      if (p == PEEKED_NONE) {
        p = doPeek();
      }

      if (p == PEEKED_BEGIN_ARRAY) {
        push(JsonScope.EMPTY_ARRAY);
        count++;
        writer.beginArray();
      } else if (p == PEEKED_BEGIN_OBJECT) {
        push(JsonScope.EMPTY_OBJECT);
        count++;
        writer.beginObject();
      } else if (p == PEEKED_END_ARRAY) {
        stackSize--;
        count--;
        writer.endArray();
      } else if (p == PEEKED_END_OBJECT) {
        stackSize--;
        count--;
        writer.endObject();
      } else if (p == PEEKED_UNQUOTED_NAME) {
        writer.name(nextUnquotedValue());
      } else if (p == PEEKED_SINGLE_QUOTED_NAME) {
        writer.name(nextQuotedValue( '\'' ));
      } else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
        writer.name(nextQuotedValue( '"' ));
      } else if (p == PEEKED_UNQUOTED) {
        writer.value(nextUnquotedValue());
      } else if (p == PEEKED_SINGLE_QUOTED) {
        writer.value(nextQuotedValue( '\'' ));
      } else if (p == PEEKED_DOUBLE_QUOTED) {
        writer.value(nextQuotedValue( '"' ));
      } else if (p == PEEKED_NUMBER) {
        // Just leave number as a string - rawValue prevents it from being escaped
        peekedString = in.substring(pos, pos + peekedNumberLength);
        writer.rawValue(peekedString);
        pos += peekedNumberLength;
      } else if (p == PEEKED_TRUE) {
        writer.value( true );
      } else if (p == PEEKED_FALSE) {
        writer.value( false );
      } else if (p == PEEKED_BUFFERED) {
        writer.value( peekedString );
      } else if (p == PEEKED_NULL) {
        writer.nullValue();
      }
      peeked = PEEKED_NONE;
    } while (count != 0);

    writer.close();
    return writer.getOutput();
  }

  /** {@inheritDoc} */
  @Override
  public Number nextNumber()
  {
    // TODO needs better handling for BigInteger and BigDecimal.
    // Use of DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS and USE_BIG_INTEGER_FOR_INTS. See NumberDeserializer of Jackson.

    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    Number result;

    if (p == PEEKED_NUMBER) {
      peekedString = in.substring(pos, pos + peekedNumberLength);
      pos += peekedNumberLength;
      p = PEEKED_BUFFERED;
    }

    if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
    } else if (p == PEEKED_UNQUOTED) {
      peekedString = nextUnquotedValue();
    } else if (p != PEEKED_BUFFERED) {
      throw new IllegalStateException("Expected a double but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    if (peekedString.equalsIgnoreCase("NAN") || peekedString.equalsIgnoreCase("INFINITY") || peekedString.equalsIgnoreCase("-INFINITY")) {
      if (lenient) {
        result = Double.parseDouble(peekedString);
      } else {
        throw syntaxError( "JSON forbids NaN and infinities: " + peekedString);
      }
    } else if (peekedString.contains( "." )) {
      // decimal
      double resultDouble = Double.parseDouble( peekedString ); // don't catch this NumberFormatException.
      if (!lenient && (Double.isNaN( resultDouble ) || Double.isInfinite( resultDouble ))) {
        throw syntaxError( "JSON forbids NaN and infinities: " + resultDouble);
      }
      result = resultDouble;
    } else {
      int length = peekedString.length();
      if (length <= 9) { // fits in int
        result = Integer.parseInt( peekedString );
      } else if (length <= 18) { // fits in long and potentially int
        long longResult = Long.parseLong( peekedString );
        if (longResult >= MIN_INT_L && longResult <= MAX_INT_L) {
          result = (int) longResult;
        } else {
          result = longResult;
        }
      } else {
        BigInteger bigIntegerResult = new BigInteger( peekedString );
        if (bigIntegerResult.signum() == -1) {
          if (bigIntegerResult.compareTo( MIN_LONG_BIGINTEGER ) >= 0) {
           result = bigIntegerResult.longValue();
          } else {
           result = bigIntegerResult;
          }
        } else {
          if (bigIntegerResult.compareTo( MAX_LONG_BIGINTEGER) <= 0) {
            result = bigIntegerResult.longValue();
          } else {
            result = bigIntegerResult;
          }
        }
      }
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public JavaScriptObject nextJavaScriptObject( boolean useSafeEval ) {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    switch (p) {
    case PEEKED_BEGIN_OBJECT:
    case PEEKED_BEGIN_ARRAY:
      JavaScriptObject result;
      int peekStack = stack.get(stackSize - 1);
      if (peekStack == JsonScope.NONEMPTY_DOCUMENT) {
        // start of the document
        String toEval = in;
        result = useSafeEval ? JsonUtils.safeEval( toEval ) : JsonUtils.unsafeEval( toEval );
        // we read everything, we move the pointer to the end of the document
        pos = toEval.length();
        peeked = PEEKED_NONE;
      } else {
        String toEval = nextValue();
        result = useSafeEval ? JsonUtils.safeEval( toEval ) : JsonUtils.unsafeEval( toEval );
      }
      return result;
    default:
      throw new IllegalStateException("Expected an array or object to evaluate a JavaScriptObject but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
  }
}
//@formatter:on
