package net.minidev.json.parser;

/*
 *    Copyright 2011 JSON-SMART authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_CHAR;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_EOF;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_TOKEN;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_UNICODE;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;

import net.minidev.json.mapper.AMapper;
import net.minidev.json.mapper.DefaultMapper;

/**
 * Parser for JSON text. Please note that JSONParser is NOT thread-safe.
 * 
 * @author Uriel Chemouni <uchemouni@gmail.com>
 */
class JSONStreamParser extends JSONBaseParser {
	public final static int EOI = -1;
	private int c;
	private Reader in;

	// len
	public JSONStreamParser(int permissifMode) {
		super(permissifMode);
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	public Object parse(Reader in) throws ParseException {
		return parse(in, DefaultMapper.DEFAULT);
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	public <T> T parse(Reader in, AMapper<T> mapper) throws ParseException {

		this.in = in;
		this.pos = 0;
		T result;
		try {
			this.c = in.read();
			result = readFirst(mapper);
		} catch (IOException e) {
			throw new ParseException(pos, e);
		}
		xs = null;
		xo = null;
		return result;
	}

	final private void read() throws IOException {
		c = in.read();
		//
		//
		pos++;
	}

	final private void readNoEnd() throws IOException, ParseException {
		c = in.read();
		//
		//
		if (c == EOI)
			throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, "EOF");
	}

	private <T> T readArray(AMapper<T> mapper) throws ParseException, IOException {
		Object current = mapper.createArray();
		if (c != '[')
			throw new RuntimeException("Internal Error");
		read();
		boolean needData = false;
		for (;;) {
			switch (c) {
			case ' ':
			case '\r':
			case '\n':
			case '\t':
				read();
				continue;
			case ']':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read(); /* unstack */
				return mapper.convert(current);
			case ':':
			case '}':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
			case ',':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read();
				needData = true;
				continue;
			case EOI:
				throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, "EOF");
			default:
				mapper.addValue(current, readMain(mapper, stopArray));
				needData = false;
				continue;
			}
		}
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	private <T> T readFirst(AMapper<T> mapper) throws ParseException, IOException {
		for (;;) {
			switch (c) {
			// skip spaces
			case ' ':
			case '\r':
			case '\n':
			case '\t':
				read();
				continue;
				// invalid stats
			case ':':
			case '}':
			case ']':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
				// start object
			case '{':
				return readObject(mapper);
				// start Array
			case '[':
				return readArray(mapper);
				// start string
			case '"':
			case '\'':
				xs = readString();
				return mapper.convert(xs);
				// string or null
			case 'n':
				xs = readNQString(stopX);
				if ("null".equals(xs)) {
					return null;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return mapper.convert(xs);
				// string or false
			case 'f':
				xs = readNQString(stopX);
				if ("false".equals(xs)) {
					return mapper.convert(Boolean.FALSE);
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return mapper.convert(xs);
				// string or true
			case 't':
				xs = readNQString(stopX);
				if ("true".equals(xs)) {
					return mapper.convert(Boolean.TRUE);
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return mapper.convert(xs);
				// string or NaN
			case 'N':
				xs = readNQString(stopX);
				if (!acceptNaN)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				if ("NaN".equals(xs)) {
					return mapper.convert(Float.valueOf(Float.NaN));
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return mapper.convert(xs);
				// digits
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case '-':
				xo = readNumber(stopX);
				return mapper.convert(xo);
			default:
				xs = readNQString(stopX);
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return mapper.convert(xs);
			}
		}
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	private Object readMain(AMapper<?> mapper, boolean stop[]) throws ParseException, IOException {
		for (;;) {
			switch (c) {
			// skip spaces
			case ' ':
			case '\r':
			case '\n':
			case '\t':
				read();
				continue;
				// invalid stats
			case ':':
			case '}':
			case ']':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
				// start object
			case '{':
				return readObject(mapper.startObject(lastKey));
				// start Array
			case '[':
				return readArray(mapper.startArray(lastKey));
				// start string
			case '"':
			case '\'':
				return readString();
			case 'n':
				xs = readNQString(stop);
				if ("null".equals(xs)) {
					return null;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
				// string or false
			case 'f':
				xs = readNQString(stop);
				if ("false".equals(xs)) {
					return Boolean.FALSE;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
				// string or true
			case 't':
				xs = readNQString(stop);
				if ("true".equals(xs)) {
					return Boolean.TRUE;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
				// string or NaN
			case 'N':
				xs = readNQString(stop);
				if (!acceptNaN)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				if ("NaN".equals(xs)) {
					return Float.valueOf(Float.NaN);
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
				// digits
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case '-':
				return readNumber(stop);
			default:
				xs = readNQString(stop);
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
		}
	}

	private String readNQString(boolean[] stop) throws IOException {
		sb.clear();
		skipNQString(stop);
		return sb.toString().trim();
	}

	private Object readNumber(boolean[] stop) throws ParseException, IOException {
		sb.clear();
		sb.append(c);// skip first char digit or -
		read();
		skipDigits();
		if (c != '.' && c != 'E' && c != 'e') {
			skipSpace();
			if (!stop[c]) {
				// convert string
				skipNQString(stop);
				xs = sb.toString().trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = sb.toString().trim();
			return parseNumber(xs);
		}
		if (c == '.') {
			sb.append(c);
			read();
			skipDigits();
		}
		if (c != 'E' && c != 'e') {
			skipSpace();
			if (!stop[c]) {
				// convert string
				skipNQString(stop);
				xs = sb.toString().trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = sb.toString().trim();
			if (!acceptLeadinZero)
				checkLeadinZero();
			if (!useHiPrecisionFloat)
				return Float.parseFloat(xs);
			if (xs.length() > 18) // follow JSjonIJ parssing methode
				return new BigDecimal(xs);
			return Double.parseDouble(xs);
		}
		sb.append('E');
		read();
		if (c == '+' || c == '-' || c >= '0' && c <= '9') {
			sb.append(c);
			read(); // skip first char
			skipDigits();
			skipSpace();
			if (!stop[c]) {
				// convert string
				skipNQString(stop);
				xs = sb.toString().trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = sb.toString().trim();
			if (!useHiPrecisionFloat)
				return Float.parseFloat(xs);
			return Double.parseDouble(xs);
		} else {
			skipNQString(stop);
			xs = sb.toString().trim();
			if (!acceptNonQuote)
				throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
			if (!acceptLeadinZero)
				checkLeadinZero();
			return xs;
		}
		// throw new ParseException(pos - 1, ERROR_UNEXPECTED_CHAR, null);
	}

	private String lastKey;

	private <T> T readObject(AMapper<T> mapper) throws ParseException, IOException {
		if (c != '{')
			throw new RuntimeException("Internal Error");
		Object current = mapper.createObject();
		boolean needData = false;
		boolean acceptData = true;
		for (;;) {
			read();
			switch (c) {
			case ' ':
			case '\r':
			case '\t':
			case '\n':
				continue;
			case ':':
			case ']':
			case '[':
			case '{':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
			case '}':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read(); /* unstack */
				return mapper.convert(current);
			case ',':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				acceptData = needData = true;
				continue;
			case '"':
			case '\'':
			default:
				String key;
				// int keyStart = pos;
				if (c == '\"' || c == '\'')
					key = readString();
				else {
					key = readNQString(stopKey);
					if (!acceptNonQuote)
						throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, key);
				}
				if (!acceptData)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, key);
				while (c != ':' && c != EOI) {
					read();
				}
				if (c == EOI)
					throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);
				readNoEnd(); /* skip : */
				lastKey = key;
				Object value = readMain(mapper, stopValue);
				mapper.setValue(current, key, value);
				lastKey = null;
				// Object duplicate = obj.put(key, readMain(stopValue));
				// if (duplicate != null)
				// throw new ParseException(keyStart,
				// ERROR_UNEXPECTED_DUPLICATE_KEY, key);
				// handler.endObjectEntry();
				// should loop skipping read step
				if (c == '}') {
					read(); /* unstack */
					return mapper.convert(current);
				}
				if (c == EOI) // Fixed on 18/10/2011 reported by vladimir
					throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);				
				// if c==, continue
				if (c == ',')
					acceptData = needData = true;
				else
					acceptData = needData = false;
				continue;
			}
		}
	}

	private String readString() throws ParseException, IOException {
		if (!acceptSimpleQuote && c == '\'') {
			if (acceptNonQuote)
				return readNQString(stopAll);
			throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
		}
		sb.clear();
		/* assert (c == '\"' || c == '\'') */
		//
		//
		//
		//
		//
		//
		//
		//
		//
		//
		//
		char sep = (char) c;
		for (;;) {
			read();
			switch (c) {
			case EOI:
				throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);
			case '"':
			case '\'':
				if (sep == c) {
					read();
					return sb.toString();
				}
				sb.append(c);
				break;
			case '\\':
				read();
				switch (c) {
				case 't':
					sb.append('\t');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'b':
					sb.append('\b');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					sb.append('/');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '"':
					sb.append('"');
					break;
				case 'u':
					sb.append(readUnicode());
					break;
				default:
					break;
				}
				break;
			case '\0': // end of string
			case (char) 1: // Start of heading
			case (char) 2: // Start of text
			case (char) 3: // End of text
			case (char) 4: // End of transmission
			case (char) 5: // Enquiry
			case (char) 6: // Acknowledge
			case (char) 7: // Bell
			case '\b': // 8: backSpase
			case '\t': // 9: horizontal tab
			case '\n': // 10: new line
			case (char) 11: // Vertical tab
			case '\f': // 12: form feed
			case '\r': // 13: return carriage
			case (char) 14: // Shift Out, alternate character set
			case (char) 15: // Shift In, resume defaultn character set
			case (char) 16: // Data link escape
			case (char) 17: // XON, with XOFF to pause listings;
			case (char) 18: // Device control 2, block-mode flow control
			case (char) 19: // XOFF, with XON is TERM=18 flow control
			case (char) 20: // Device control 4
			case (char) 21: // Negative acknowledge
			case (char) 22: // Synchronous idle
			case (char) 23: // End transmission block, not the same as EOT
			case (char) 24: // Cancel line, MPE echoes !!!
			case (char) 25: // End of medium, Control-Y interrupt
			case (char) 26: // Substitute
			case (char) 27: // escape
			case (char) 28: // File Separator
			case (char) 29: // Group Separator
			case (char) 30: // Record Separator
			case (char) 31: // Unit Separator
			case (char) 127: // del
				if (ignoreControlChar)
					continue;
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
			default:
				sb.append(c);
			}
		}
	}

	private char readUnicode() throws ParseException, IOException {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			value = value * 16;
			read();
			if (c >= '0' && c <= '9')
				value += c - '0';
			else if (c >= 'A' && c <= 'F')
				value += (c - 'A') + 10;
			else if (c >= 'a' && c <= 'f')
				value += (c - 'a') + 10;
			else if (c == EOI)
				throw new ParseException(pos, ERROR_UNEXPECTED_EOF, "EOF");
			else
				throw new ParseException(pos, ERROR_UNEXPECTED_UNICODE, c);
		}
		return (char) value;
	}

	private void skipDigits() throws IOException {
		for (;;) {
			if (c == EOI)
				return;
			if (c < '0' || c > '9')
				return;
			sb.append(c);
			read();
		}
	}

	private void skipNQString(boolean[] stop) throws IOException {
		for (;;) {
			if (c == EOI)
				return;
			if (c >= 0 && c <= 125 && stop[c])
				return;
			sb.append(c);
			read();
		}
	}

	private void skipSpace() throws IOException {
		for (;;) {
			if (c == EOI)
				return;
			if (c != ' ' && c != '\r' && c != '\t' && c != '\n')
				return;
			sb.append(c);
			read();
		}
	}
}
