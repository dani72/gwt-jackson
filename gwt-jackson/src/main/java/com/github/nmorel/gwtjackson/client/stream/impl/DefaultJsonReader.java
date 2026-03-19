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
 * A JSON reader that uses a sliding character buffer for streaming input.
 *
 * @author Jesse Wilson
 * @since 1.6
 * @version $Id: $
 */
public class DefaultJsonReader extends AbstractJsonReader
{
  /** The input JSON. */
  private final StringReader in;

  /**
   * Use a manual buffer to easily read and unread upcoming characters, and
   * also so we can create strings without an intermediate StringBuilder.
   * We decode literals directly out of this buffer, so it must be at least as
   * long as the longest token that can be reported as a number.
   */
  private final char[] buffer = new char[1024];

  /**
   * A peeked value that was composed entirely of digits with an optional
   * leading dash. Positive values may not have a leading 0.
   */
  private long peekedLong;

  /**
   * Creates a new instance that reads a JSON-encoded stream from {@code in}.
   *
   * @param in a {@link com.github.nmorel.gwtjackson.client.stream.impl.StringReader} object.
   */
  public DefaultJsonReader( StringReader in ) {
    if (in == null) {
      throw new NullPointerException("in == null");
    }
    this.in = in;
  }

  // ---- Abstract method implementations ----

  @Override
  protected char charAt(int pos) {
    return buffer[pos];
  }

  @Override
  protected boolean ensureAvailable(int minimum) {
    return pos + minimum <= limit || fillBuffer(minimum);
  }

  @Override
  protected String extractString(int start, int length) {
    return new String(buffer, start, length);
  }

  @Override
  protected String getInputString() {
    return in.getInput();
  }

  /** {@inheritDoc} */
  @Override
  public String getInput(){
    return in.getInput();
  }

  @Override
  protected int nextNonWhitespace(boolean throwOnEof)
  {
    /*
     * This code uses ugly local variables 'p' and 'l' representing the 'pos'
     * and 'limit' fields respectively. Using locals rather than fields saves
     * a few field reads for each whitespace character in a pretty-printed
     * document, resulting in a 5% speedup. We need to flush 'p' to its field
     * before any (potentially indirect) call to fillBuffer() and reread both
     * 'p' and 'l' after any (potentially indirect) call to the same method.
     */
    char[] buffer = this.buffer;
    int p = pos;
    int l = limit;
    while (true) {
      if (p == l) {
        pos = p;
        if (!fillBuffer(1)) {
          break;
        }
        p = pos;
        l = limit;
      }

      int c = buffer[p++];
      if (c == '\n') {
        lineNumber++;
        lineStart = p;
        continue;
      } else if (c == ' ' || c == '\r' || c == '\t') {
        continue;
      }

      if (c == '/') {
        pos = p;
        if (p == l) {
          pos--; // push back '/' so it's still in the buffer when this method returns
          boolean charsLoaded = fillBuffer(2);
          pos++; // consume the '/' again
          if (!charsLoaded) {
            return c;
          }
        }

        checkLenient();
        char peek = buffer[pos];
        switch (peek) {
        case '*':
          // skip a /* c-style comment */
          pos++;
          if (!skipTo("*/")) {
            throw syntaxError("Unterminated comment");
          }
          p = pos + 2;
          l = limit;
          continue;

        case '/':
          // skip a // end-of-line comment
          pos++;
          skipToEndOfLine();
          p = pos;
          l = limit;
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
        l = limit;
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
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    char[] buffer = this.buffer;
    StringBuilder builder = new StringBuilder();
    while (true) {
      int p = pos;
      int l = limit;
      /* the index of the first character not yet appended to the builder. */
      int start = p;
      while (p < l) {
        int c = buffer[p++];

        if (c == quote) {
          pos = p;
          builder.append(buffer, start, p - start - 1);
          return builder.toString();
        } else if (c == '\\') {
          pos = p;
          builder.append(buffer, start, p - start - 1);
          builder.append(readEscapeCharacter());
          p = pos;
          l = limit;
          start = p;
        } else if (c == '\n') {
          lineNumber++;
          lineStart = p;
        }
      }

      builder.append(buffer, start, p - start);
      pos = p;
      if (!fillBuffer(1)) {
        throw syntaxError("Unterminated string");
      }
    }
  }

  @SuppressWarnings("fallthrough")
  @Override
  protected String nextUnquotedValue()
  {
    StringBuilder builder = null;
    int i = 0;

    findNonLiteralCharacter:
    while (true) {
      for (; pos + i < limit; i++) {
        switch (buffer[pos + i]) {
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

      // Attempt to load the entire literal into the buffer at once.
      if (i < buffer.length) {
        if (fillBuffer(i + 1)) {
          continue;
        } else {
          break;
        }
      }

      // use a StringBuilder when the value is too long. This is too long to be a number!
      if (builder == null) {
        builder = new StringBuilder();
      }
      builder.append(buffer, pos, i);
      pos += i;
      i = 0;
      if (!fillBuffer(1)) {
        break;
      }
    }

    String result;
    if (builder == null) {
      result = new String(buffer, pos, i);
    } else {
      builder.append(buffer, pos, i);
      result = builder.toString();
    }
    pos += i;
    return result;
  }

  @Override
  protected void skipQuotedValue(char quote)
  {
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    char[] buffer = this.buffer;
    do {
      int p = pos;
      int l = limit;
      /* the index of the first character not yet appended to the builder. */
      while (p < l) {
        int c = buffer[p++];
        if (c == quote) {
          pos = p;
          return;
        } else if (c == '\\') {
          pos = p;
          readEscapeCharacter();
          p = pos;
          l = limit;
        } else if (c == '\n') {
          lineNumber++;
          lineStart = p;
        }
      }
      pos = p;
    } while (fillBuffer(1));
    throw syntaxError("Unterminated string");
  }

  @Override
  protected void skipUnquotedValue()
  {
    do {
      int i = 0;
      for (; pos + i < limit; i++) {
        switch (buffer[pos + i]) {
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
    } while (fillBuffer(1));
  }

  @Override
  protected char readEscapeCharacter()
  {
    if (pos == limit && !fillBuffer(1)) {
      throw syntaxError("Unterminated escape sequence");
    }

    char escaped = buffer[pos++];
    switch (escaped) {
    case 'u':
      if (pos + 4 > limit && !fillBuffer(4)) {
        throw syntaxError("Unterminated escape sequence");
      }
      // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
      char result = 0;
      for (int i = pos, end = i + 4; i < end; i++) {
        char c = buffer[i];
        result <<= 4;
        if (c >= '0' && c <= '9') {
          result += (c - '0');
        } else if (c >= 'a' && c <= 'f') {
          result += (c - 'a' + 10);
        } else if (c >= 'A' && c <= 'F') {
          result += (c - 'A' + 10);
        } else {
          throw new NumberFormatException("\\u" + new String(buffer, pos, 4));
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
    while (pos < limit || fillBuffer(1)) {
      char c = buffer[pos++];
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
    for (; pos + toFind.length() <= limit || fillBuffer(toFind.length()); pos++) {
      if (buffer[pos] == '\n') {
        lineNumber++;
        lineStart = pos + 1;
        continue;
      }
      for (int c = 0; c < toFind.length(); c++) {
        if (buffer[pos + c] != toFind.charAt(c)) {
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

    if (pos + NON_EXECUTE_PREFIX.length > limit && !fillBuffer(NON_EXECUTE_PREFIX.length)) {
      return;
    }

    for (int i = 0; i < NON_EXECUTE_PREFIX.length; i++) {
      if (buffer[pos + i] != NON_EXECUTE_PREFIX[i]) {
        return; // not a security token!
      }
    }

    // we consumed a security token!
    pos += NON_EXECUTE_PREFIX.length;
  }

  @Override
  protected int peekNumber()
  {
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    char[] buffer = this.buffer;
    int p = pos;
    int l = limit;

    long value = 0; // Negative to accommodate Long.MIN_VALUE more easily.
    boolean negative = false;
    boolean fitsInLong = true;
    int last = NUMBER_CHAR_NONE;

    int i = 0;

    charactersOfNumber:
    for (; true; i++) {
      if (p + i == l) {
        if (i == buffer.length) {
          // Though this looks like a well-formed number, it's too long to continue reading. Give up
          // and let the application handle this as an unquoted literal.
          return PEEKED_NONE;
        }
        if (!fillBuffer(i + 1)) {
          break;
        }
        p = pos;
        l = limit;
      }

      char c = buffer[p + i];
      switch (c) {
      case '-':
        if (last == NUMBER_CHAR_NONE) {
          negative = true;
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
          value = -(c - '0');
          last = NUMBER_CHAR_DIGIT;
        } else if (last == NUMBER_CHAR_DIGIT) {
          if (value == 0) {
            return PEEKED_NONE; // Leading '0' prefix is not allowed (since it could be octal).
          }
          long newValue = value * 10 - (c - '0');
          fitsInLong &= value > MIN_INCOMPLETE_INTEGER
              || (value == MIN_INCOMPLETE_INTEGER && newValue < value);
          value = newValue;
        } else if (last == NUMBER_CHAR_DECIMAL) {
          last = NUMBER_CHAR_FRACTION_DIGIT;
        } else if (last == NUMBER_CHAR_EXP_E || last == NUMBER_CHAR_EXP_SIGN) {
          last = NUMBER_CHAR_EXP_DIGIT;
        }
      }
    }

    // We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
    if (last == NUMBER_CHAR_DIGIT && fitsInLong && (value != Long.MIN_VALUE || negative)) {
      peekedLong = negative ? value : -value;
      pos += i;
      return peeked = PEEKED_LONG;
    } else if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT
        || last == NUMBER_CHAR_EXP_DIGIT) {
      peekedNumberLength = i;
      return peeked = PEEKED_NUMBER;
    } else {
      return PEEKED_NONE;
    }
  }

  // ---- Buffer management ----

  /**
   * Returns true once {@code limit - pos >= minimum}. If the data is
   * exhausted before that many characters are available, this returns
   * false.
   */
  private boolean fillBuffer(int minimum)
  {
    char[] buffer = this.buffer;
    lineStart -= pos;
    if (limit != pos) {
      limit -= pos;
      System.arraycopy( buffer, pos, buffer, 0, limit );
    } else {
      limit = 0;
    }

    pos = 0;
    int total;
    while ((total = in.read(buffer, limit, buffer.length - limit)) != -1) {
      limit += total;

      // if this is the first read, consume an optional byte order mark (BOM) if it exists
      if (lineNumber == 0 && lineStart == 0 && limit > 0 && buffer[0] == '\ufeff') {
        pos++;
        lineStart++;
        minimum++;
      }

      if (limit >= minimum) {
        return true;
      }
    }
    return false;
  }

  // ---- Methods with PEEKED_LONG-specific handling ----

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
    } else if (p == PEEKED_LONG) {
      result = Long.toString( peekedLong );
    } else if (p == PEEKED_NUMBER) {
      result = new String(buffer, pos, peekedNumberLength);
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

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      return (double) peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = new String(buffer, pos, peekedNumberLength);
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
    double result = Double.parseDouble( peekedString ); // don't catch this NumberFormatException.
    if (!lenient && (Double.isNaN( result ) || Double.isInfinite( result ))) {
      throw syntaxError( "JSON forbids NaN and infinities: " + result);
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public long nextLong()
  {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      return peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = new String(buffer, pos, peekedNumberLength);
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
    } else {
      throw new IllegalStateException("Expected a long but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble( peekedString ); // don't catch this NumberFormatException.
    long result = (long) asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'long'.
      throw new NumberFormatException("Expected a long but was " + peekedString
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
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
    if (p == PEEKED_LONG) {
      result = (int) peekedLong;
      if (peekedLong != result) { // Make sure no precision was lost casting to 'int'.
        throw new NumberFormatException("Expected an int but was " + peekedLong
            + " at line " + getLineNumber() + " column " + getColumnNumber());
      }
      peeked = PEEKED_NONE;
      return result;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = new String(buffer, pos, peekedNumberLength);
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
    } else {
      throw new IllegalStateException("Expected an int but was " + peek()
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble( peekedString ); // don't catch this NumberFormatException.
    result = (int) asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
      throw new NumberFormatException("Expected an int but was " + peekedString
          + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
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

    JsonWriter writer = new DefaultJsonWriter( new StringBuilder() );
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
        writer.name( nextUnquotedValue() );
      } else if (p == PEEKED_SINGLE_QUOTED_NAME) {
        writer.name( nextQuotedValue( '\'' ) );
      } else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
        writer.name( nextQuotedValue( '"' ) );
      } else if (p == PEEKED_UNQUOTED) {
        writer.value( nextUnquotedValue() );
      } else if (p == PEEKED_SINGLE_QUOTED) {
        writer.value(nextQuotedValue( '\'' ));
      } else if (p == PEEKED_DOUBLE_QUOTED) {
        writer.value(nextQuotedValue( '"' ));
      } else if (p == PEEKED_NUMBER) {
        writer.value( new String(buffer, pos, peekedNumberLength) );
        pos += peekedNumberLength;
      } else if (p == PEEKED_TRUE) {
        writer.value(true);
      } else if (p == PEEKED_FALSE) {
        writer.value( false );
      } else if (p == PEEKED_LONG) {
        writer.value( peekedLong );
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
    if (p == PEEKED_LONG) {
      if (peekedLong < 0l) {
        if (peekedLong >= MIN_INT_L) {
           result = (int) peekedLong;
        } else {
            result = peekedLong;
        }
      } else {
        if (peekedLong <= MAX_INT_L) {
          result = (int) peekedLong;
        } else {
          result = peekedLong;
        }
      }
      peeked = PEEKED_NONE;
      return result;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = new String(buffer, pos, peekedNumberLength);
      pos += peekedNumberLength;
      peeked = PEEKED_BUFFERED;
      result = Double.parseDouble( peekedString );
      peekedString = null;
      peeked = PEEKED_NONE;
      return result;
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
    if (peekedString.contains( "." )) {
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
        if(length == 10) { // can fits in int
          if (longResult < 0l) {
            if (longResult >= MIN_INT_L) {
             result = (int) longResult;
            } else {
             result = longResult;
            }
          } else {
            if (longResult <= MAX_INT_L) {
              result = (int) longResult;
            } else {
              result = longResult;
            }
          }
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
        String toEval = in.getInput();
        result = useSafeEval ? JsonUtils.safeEval( toEval ) : JsonUtils.unsafeEval( toEval );
        // we read everything, we move the pointer to the end of the document
        pos = toEval.length();
        limit = toEval.length();
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
