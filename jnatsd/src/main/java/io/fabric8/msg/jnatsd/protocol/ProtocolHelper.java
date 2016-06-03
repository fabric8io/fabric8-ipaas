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

import java.util.HashMap;
import java.util.Map;

public class ProtocolHelper {

    public static Map<String, String> parse(Buffer buffer, int start, int end) {
        Map<String, String> result = new HashMap();
        String command = buffer.getString(start, end);
        command = command.substring(command.indexOf('{') + 1);
        command = command.substring(0, command.lastIndexOf('}'));

        String[] parameters = command.split(",");
        for (String pair : parameters) {
            pair = pair.trim();
            String[] parts = pair.split(":", 2);
            String key = parts[0].trim();
            String val = parts[1].trim();

            // trim the quotes
            int lastQuotePos = key.lastIndexOf("\"");
            key = key.substring(1, lastQuotePos);

            // bools and numbers may not have quotes.
            if (val.startsWith("\"")) {
                lastQuotePos = val.lastIndexOf("\"");
                val = val.substring(1, lastQuotePos);
            }
            result.put(key, val);
        }
        return result;
    }

    public static int skipWhiteSpace(Buffer buffer, int start, int end) {
        int i = start;
        while (i < end) {
            if (isWhiteSpace(buffer.getByte(i))) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    public static boolean isWhiteSpace(byte b) {
        return Character.isWhitespace((int) b);
    }

}
