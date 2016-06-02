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

package io.fabric8.msg.jnatsd.protocol;

import io.vertx.core.buffer.Buffer;

public class BufferWrapper {
    private final Buffer buffer;
    private final int start;
    private final int end;

    public BufferWrapper(Buffer buffer, int start, int end) {
        this.buffer = buffer;
        this.start = start;
        this.end = end;
    }

    /**
     * Create a new BufferWrapper using existing offsets but new buffer
     *
     * @return
     */
    public BufferWrapper rewrap(Buffer buffer) {
        return new BufferWrapper(buffer, start, end);
    }

    public void appendTo(Buffer buffer) {
        buffer.appendBuffer(this.buffer, start, (end - start));
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public byte getByte(int i) {
        return buffer.getByte(i + start);
    }

    @Override
    public String toString() {
        if (buffer != null && end > start) {
            return "BufferWrapper[" + start + "->" + end + "] " + buffer.getString(start, end);
        }
        return "BufferWapper[empty]";
    }

    public int parseToInt() throws NumberFormatException {
        if (buffer == null) {
            throw new NumberFormatException("null");
        }

        int result = 0;
        boolean negative = false;
        int i = start;
        int limit = -Integer.MAX_VALUE;
        int multmin;
        int digit;

        if (end > start) {
            char firstChar = (char) buffer.getByte(i);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (firstChar != '+')
                    throw new NumberFormatException(buffer.toString());

                if (end == 1) // Cannot have lone "+" or "-"
                    throw new NumberFormatException(buffer.toString());
                i++;
            }
            multmin = limit / 10;
            while (i < end) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                digit = Character.digit(buffer.getByte(i++), 10);
                if (digit < 0) {
                    throw new NumberFormatException(buffer.toString());
                }
                if (result < multmin) {
                    throw new NumberFormatException(buffer.toString());
                }
                result *= 10;
                if (result < limit + digit) {
                    throw new NumberFormatException(buffer.toString());
                }
                result -= digit;
            }
        } else {
            throw new NumberFormatException(buffer.toString());
        }
        return negative ? result : -result;
    }

    @Override
    public int hashCode() {
        int result = buffer.hashCode();
        result = 31 * result + start;
        result = 31 * result + end;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BufferWrapper that = (BufferWrapper) o;

        if (start != that.start) return false;
        if (end != that.end) return false;
        return buffer.equals(that.buffer);

    }

}
