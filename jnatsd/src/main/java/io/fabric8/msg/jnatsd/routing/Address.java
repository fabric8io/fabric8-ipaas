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

import io.fabric8.msg.jnatsd.protocol.BufferWrapper;
import io.vertx.core.buffer.Buffer;

import java.util.Arrays;

public class Address implements Comparable<Address> {
    private final char[] array;
    private final int hash;

    public Address(String string) {
        this(string.toCharArray());
    }

    public Address(BufferWrapper buffer) {
        array = new char[buffer.length()];
        int hashValue = 1;
        for (int i = 0; i < buffer.length(); i++) {
            char c = (char) buffer.getByte(i);
            array[i] = c;
            hashValue = 31 * hashValue + c;

        }
        hash = hashValue;
    }

    public Address(char[] ca) {
        this.array = ca;
        hash = Arrays.hashCode(array);
    }

    public boolean isMatch(Address match) {
        return WildCard.isMatch(array, match.array);
    }

    public int size() {
        return array.length;
    }

    public void appendToBuffer(Buffer buffer
    ) {
        for (int i = 0; i < array.length; i++) {
            buffer.appendByte((byte) array[i]);
        }
    }

    @Override
    public final boolean equals(Object obj) {
        boolean result = false;
        if (obj != null && obj instanceof Address) {
            Address other = (Address) obj;
            if (array != null && other.array != null && array.length == other.array.length) {
                result = true;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] != other.array[i]) {
                        result = false;
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public int compareTo(Address anotherAddress) {
        int len1 = array.length;
        int len2 = anotherAddress.array.length;
        int lim = Math.min(len1, len2);
        char v1[] = array;
        char v2[] = anotherAddress.array;

        int k = 0;
        while (k < lim) {
            char c1 = v1[k];
            char c2 = v2[k];
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }

    @Override
    public String toString() {
        return new String(array);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
