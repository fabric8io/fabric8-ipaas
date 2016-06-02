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

public class BufferTokenizerTest {
    private Buffer test = Buffer.buffer(" foo 5\r\nTest0\r\n");

    public void countTokens() throws Exception {
        BufferTokenizer bufferTokenizer = new BufferTokenizer(test);
        Assert.assertEquals(3, bufferTokenizer.countTokens());
    }

    @Test
    public void listTokens() throws Exception {
        BufferTokenizer bufferTokenizer = new BufferTokenizer(test);
        while (bufferTokenizer.hasMoreTokens()) {
            System.err.println("{" + bufferTokenizer.nextToken() + "}");
        }
    }

}
