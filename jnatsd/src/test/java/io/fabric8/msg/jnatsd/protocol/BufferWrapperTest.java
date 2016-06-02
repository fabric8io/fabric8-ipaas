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
import org.junit.Assert;
import org.junit.Test;

public class BufferWrapperTest {
    @Test
    public void rewrap() throws Exception {

    }

    @Test
    public void appendTo() throws Exception {
        Buffer buffer = Buffer.buffer("This is a test ");
        Buffer test = Buffer.buffer();
        test.appendBytes(new byte[2]);
        test.appendString("foo");
        BufferWrapper bufferWrapper = new BufferWrapper(test, 2, test.length());
        bufferWrapper.appendTo(buffer);

        System.err.println(buffer);
    }

    @Test
    public void parseIntToInt() throws Exception {
        Buffer buffer = Buffer.buffer("Test 41 ");
        BufferWrapper bufferWrapper = new BufferWrapper(buffer, 5, 7);
        System.err.println(bufferWrapper);

        int test = bufferWrapper.parseToInt();

        Assert.assertEquals(41, test);
    }

}
