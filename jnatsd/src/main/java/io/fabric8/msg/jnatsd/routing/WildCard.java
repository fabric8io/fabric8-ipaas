/*
 *
 *  * Copyright 2005-2016 Red Hat, Inc.
 *  * Red Hat licenses this file to you under the Apache License, version
 *  * 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  * implied.  See the License for the specific language governing
 *  * permissions and limitations under the License.
 *
 */

package io.fabric8.msg.jnatsd.routing;

import java.util.ArrayList;
import java.util.List;

public final class WildCard {

    static final char MATCH_ELEMENT = '*';
    static final char MATCH_ALL = '>';
    static final char DELIMETER = '.';

    public static final boolean
    isMatch(String address, String match) {
        return isMatch(address.toCharArray(), match.toCharArray());
    }

    public static final boolean
    isMatch(String address, char[] match) {
        return isMatch(address.toCharArray(), match);
    }

    public static final boolean
    isMatch(char[] address, String match) {
        return isMatch(address, match.toCharArray());
    }

    public static final boolean
    isMatch(char[] address, char[] match) {
        return address != null && match != null && doMatch(address, 0, address.length, match, 0, match.length);
    }

    public static final boolean
    containsReservedCharacters(char[] address) {
        return address != null ? containsReservedCharacters(address, 0, address.length) : false;
    }

    public static List<char[]> getTokens(char[] address) {
        List<char[]> result = new ArrayList<>();
        int last = 0;
        for (int i = 0; i < address.length; i++) {
            if (isDelimiter(address[i])) {
                if (i > last) {
                    int size = i - last;
                    char[] token = new char[size];
                    System.arraycopy(address, last, token, 0, size);
                    result.add(token);
                }
                last = i;
            }
        }
        if (result.isEmpty()) {
            result.add(address);
        }
        return result;
    }

    public static final boolean
    containsReservedCharacters(char[] address, int offset, int count) {
        if (address != null) {

            for (int i = offset; i < count; i++) {
                char c = address[i];
                if (c == MATCH_ALL || c == MATCH_ELEMENT) {
                    return true;
                }
            }
        }
        return false;
    }

    //-------------------------------------------------------------------------
    //   Implementation methods
    //------------------------------------------------------------------------

    static final boolean
    doMatch(char[] address, int aoff, int alen, char[] match, int moff, int mlen) {
        boolean result = true;
        boolean matchall = false;

        if (address == null || match == null) {
            return false;
        }

        while (aoff < alen && moff < mlen) {

            char matchchar = match[moff];
            char addresschar = address[aoff];

            if (matchchar != addresschar ||
                    matchchar == MATCH_ALL ||
                    addresschar == MATCH_ALL ||
                    matchchar == MATCH_ELEMENT ||
                    addresschar == MATCH_ELEMENT) {
                if (matchchar == MATCH_ALL || addresschar == MATCH_ALL) {
                    matchall = true;
                    break;
                } else if ((matchchar == MATCH_ELEMENT || addresschar == MATCH_ELEMENT) &&
                               (isMatchElement(match, moff, mlen) ||
                                    isMatchElement(address, aoff, alen))) {
                    if (containsDelimeter(address, aoff, alen) == false &&
                            containsDelimeter(match, moff, mlen) == false) {
                        break;
                    } else {
                        moff = offsetToNextToken(match, moff, mlen);
                        aoff = offsetToNextToken(address, aoff, alen);
                        continue;
                    }
                } else {
                    result = false;
                    break;
                }
            }
            moff++;
            aoff++;
        }
        if (result &&
                (mlen != alen && moff != mlen || aoff != alen)
                && !matchall) {
            result = isMatchAll(match, moff, mlen) || isMatchAll(address, aoff, alen);
        }

        return result;
    }

    static final boolean
    isMatchAll(char[] str, int offset, int count) {
        boolean result = false;
        if (str != null && offset < str.length) {
            if (offset + 1 < str.length && isDelimiter(str[offset])) {
                if (str[offset + 1] == MATCH_ALL) {
                    result = true;
                }
            }//else if ( (str[offset] == MATCH_ALL || str[offset] == MATCH_ELEMENT )
            else if ((str[offset] == MATCH_ALL)
                         && (isWhiteSpace(str, offset + 1, count) ||
                                 isDelimiter(str[offset + 1]) ||
                                 offset + 1 == str.length)) {
                result = true;
            }
        }
        return result;
    }

    static final boolean
    isMatchElement(char[] str, int offset, int count) {
        boolean result = false;
        if (str[offset] == MATCH_ELEMENT) {
            if (offset == 0 || isDelimiter(str[offset - 1])) {
                result = ((offset + 1) >= str.length || isDelimiter(str[offset + 1]) ||
                              isWhiteSpace(str, offset + 1, count));
            }
        }
        return result;
    }

    private static final boolean
    isWhiteSpace(char[] str, int offset, int len) {
        while ((offset < len)) {
            if (!Character.isWhitespace(str[offset++])) {
                return false;
            }
        }
        return true;
    }

    static final int
    offsetToNextToken(char[] str, int offset, int len) {
        while (offset < len) {
            if (isDelimiter(str[offset]))
                break;
            offset++;
        }
        return offset;
    }

    static final int
    offsetToNextElement(char[] str, int offset, int len) {
        int result = -1;
        int count = offset;
        while (count < len) {
            if (isDelimiter(str[count])) {
                result = ++count;
                while (count < len && isDelimiter(str[count])) {
                    result = ++count;
                }
                break;
            }
            count++;
        }
        return result;
    }

    private static final boolean
    containsDelimeter(char[] str, int offset, int len) {
        boolean result = false;
        while (offset < len) {
            if (isDelimiter(str[offset++])) {
                result = true;
                break;
            }
        }
        return result;
    }

    private static final boolean isDelimiter(char c) {
        return c == DELIMETER;
    }

}

