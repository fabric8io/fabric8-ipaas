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

import static io.fabric8.msg.jnatsd.protocol.ProtocolHelper.isWhiteSpace;

public class BufferTokenizer {
    private int currentPosition;
    private int newPosition;
    private Buffer buffer;
    private int end;

    public BufferTokenizer(Buffer buffer) {
        this(buffer, 0, buffer.length());
    }

    public BufferTokenizer(Buffer buffer, int start, int end) {
        currentPosition = start;
        newPosition = -1;
        this.buffer = buffer;
        this.end = end;
    }

    public boolean hasMoreTokens() {
        newPosition = skipToNextToken(currentPosition);
        return (newPosition < end);
    }

    public BufferWrapper nextToken() {

        currentPosition = skipToNextToken(currentPosition);
        newPosition = -1;

        if (currentPosition >= end)
            return null;
        int start = currentPosition;
        int stop = scanToken(currentPosition);
        currentPosition = scanToken(currentPosition);
        return new BufferWrapper(buffer, start, stop);
    }

    public int countTokens() {
        int count = 0;
        int currpos = currentPosition;
        while (currpos < end) {
            currpos = skipToNextToken(currpos);
            if (currpos >= end)
                break;
            currpos = scanToken(currpos);
            count++;
        }
        return count;
    }

    private int skipToNextToken(int startPos) {

        int position = startPos;
        while (position < end) {
            if (!isWhiteSpace(buffer.getByte(position))) {
                break;
            }
            position++;
        }
        return position;
    }

    private int scanToken(int startPos) {
        int position = startPos;
        while (position < end) {
            if (isWhiteSpace(buffer.getByte(position))) {
                break;
            }
            position++;
        }
        return position;
    }

}
