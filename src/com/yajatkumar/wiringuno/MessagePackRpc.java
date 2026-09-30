/*
 * Copyright (C) 2026 Yajat Kumar <yajatapps3@gmail.com>
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

package com.yajatkumar.wiringuno;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight, zero-dependency MessagePack-RPC encoder & decoder
 * for Arduino Router communication.
 */
public class MessagePackRpc {

    public static final int TYPE_REQUEST = 0;
    public static final int TYPE_RESPONSE = 1;
    public static final int TYPE_NOTIFICATION = 2;

    public static class Response {
        public final int msgId;
        public final boolean isSuccess;
        public final String error;
        public final Object result;

        public Response(int msgId, boolean isSuccess, String error, Object result) {
            this.msgId = msgId;
            this.isSuccess = isSuccess;
            this.error = error;
            this.result = result;
        }

        @Override
        public String toString() {
            if (isSuccess) {
                return "Response(id=" + msgId + ", success=true, result=" + result + ")";
            } else {
                return "Response(id=" + msgId + ", error=" + error + ")";
            }
        }
    }

    /**
     * Encode a set_pin_by_name(String pin, boolean state) request.
     * Matches the official MessagePack-RPC frame: [0, msgId, "set_pin_by_name", [pin, state]]
     */
    public static byte[] encodeSetPinByName(int msgId, String pinName, boolean state) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Fixarray with 4 elements [type, msgid, method, params]
            out.write(0x94);

            // Type 0 (REQUEST)
            out.write(0x00);

            // MsgID
            writeInteger(out, msgId);

            // Method: "set_pin_by_name"
            writeString(out, "set_pin_by_name");

            // Params array: [pinName, state] -> fixarray with 2 elements
            out.write(0x92);
            writeString(out, pinName);
            out.write(state ? 0xC3 : 0xC2);

        } catch (IOException e) {
            throw new RuntimeException("Encoding error", e);
        }
        return out.toByteArray();
    }

    /**
     * Encode a generic request [0, msgId, method, [params...]]
     */
    public static byte[] encodeRequest(int msgId, String method, Object... params) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(0x94); // fixarray of 4
            out.write(0x00); // REQUEST
            writeInteger(out, msgId);
            writeString(out, method);

            // Params array
            writeArrayHeader(out, params != null ? params.length : 0);
            if (params != null) {
                for (Object param : params) {
                    writeObject(out, param);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Encoding error", e);
        }
        return out.toByteArray();
    }

    private static void writeObject(ByteArrayOutputStream out, Object obj) throws IOException {
        if (obj == null) {
            out.write(0xC0); // nil
        } else if (obj instanceof Boolean) {
            out.write(((Boolean) obj) ? 0xC3 : 0xC2);
        } else if (obj instanceof Integer) {
            writeInteger(out, (Integer) obj);
        } else if (obj instanceof Long) {
            writeLong(out, (Long) obj);
        } else if (obj instanceof String) {
            writeString(out, (String) obj);
        } else {
            writeString(out, obj.toString());
        }
    }

    private static void writeInteger(ByteArrayOutputStream out, int val) throws IOException {
        if (val >= 0 && val <= 0x7F) {
            out.write(val);
        } else if (val >= -32 && val < 0) {
            out.write(val & 0xFF);
        } else if (val >= 0 && val <= 0xFF) {
            out.write(0xCC); // uint 8
            out.write(val);
        } else if (val >= 0 && val <= 0xFFFF) {
            out.write(0xCD); // uint 16
            out.write((val >> 8) & 0xFF);
            out.write(val & 0xFF);
        } else {
            out.write(0xCE); // uint 32
            out.write((val >> 24) & 0xFF);
            out.write((val >> 16) & 0xFF);
            out.write((val >> 8) & 0xFF);
            out.write(val & 0xFF);
        }
    }

    private static void writeLong(ByteArrayOutputStream out, long val) throws IOException {
        if (val >= 0 && val <= Integer.MAX_VALUE) {
            writeInteger(out, (int) val);
        } else {
            out.write(0xCF); // uint 64
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((val >> (i * 8)) & 0xFF));
            }
        }
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        if (len < 32) {
            out.write(0xA0 | len);
        } else if (len <= 0xFF) {
            out.write(0xD9);
            out.write(len);
        } else if (len <= 0xFFFF) {
            out.write(0xDA);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0xDB);
            out.write((len >> 24) & 0xFF);
            out.write((len >> 16) & 0xFF);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        out.write(bytes);
    }

    private static void writeArrayHeader(ByteArrayOutputStream out, int size) throws IOException {
        if (size < 16) {
            out.write(0x90 | size);
        } else if (size <= 0xFFFF) {
            out.write(0xDC);
            out.write((size >> 8) & 0xFF);
            out.write(size & 0xFF);
        } else {
            out.write(0xDD);
            out.write((size >> 24) & 0xFF);
            out.write((size >> 16) & 0xFF);
            out.write((size >> 8) & 0xFF);
            out.write(size & 0xFF);
        }
    }

    /**
     * Parse a MessagePack-RPC Response buffer: [1, msgid, error, result]
     */
    public static Response parseResponse(byte[] data, int offset, int length) {
        if (data == null || length < 4) {
            return null;
        }

        int pos = offset;
        int b0 = data[pos++] & 0xFF;

        // Expect fixarray of 4 elements (0x94)
        if (b0 != 0x94) {
            return null;
        }

        // Type: 1 = RESPONSE
        int type = data[pos++] & 0xFF;
        if (type != TYPE_RESPONSE) {
            return null;
        }

        // MsgID
        int msgId = 0;
        int idByte = data[pos++] & 0xFF;
        if (idByte <= 0x7F) {
            msgId = idByte;
        } else if (idByte == 0xCC) {
            msgId = data[pos++] & 0xFF;
        } else if (idByte == 0xCD) {
            msgId = ((data[pos++] & 0xFF) << 8) | (data[pos++] & 0xFF);
        } else if (idByte == 0xCE) {
            msgId = ((data[pos++] & 0xFF) << 24) | ((data[pos++] & 0xFF) << 16)
                    | ((data[pos++] & 0xFF) << 8) | (data[pos++] & 0xFF);
        }

        // Error
        int errByte = data[pos++] & 0xFF;
        boolean isSuccess = (errByte == 0xC0); // 0xC0 is nil
        String errorMsg = null;
        if (!isSuccess) {
            if ((errByte & 0xE0) == 0xA0) {
                int strLen = errByte & 0x1F;
                int readLen = Math.min(strLen, (offset + length) - pos);
                errorMsg = new String(data, pos, readLen, StandardCharsets.UTF_8);
                pos += readLen;
            } else if (errByte == 0xD9 && pos < offset + length) {
                int strLen = data[pos++] & 0xFF;
                int readLen = Math.min(strLen, (offset + length) - pos);
                errorMsg = new String(data, pos, readLen, StandardCharsets.UTF_8);
                pos += readLen;
            } else if (errByte == 0xDA && pos + 1 < offset + length) {
                int strLen = ((data[pos++] & 0xFF) << 8) | (data[pos++] & 0xFF);
                int readLen = Math.min(strLen, (offset + length) - pos);
                errorMsg = new String(data, pos, readLen, StandardCharsets.UTF_8);
                pos += readLen;
            } else {
                errorMsg = "Error: 0x" + Integer.toHexString(errByte);
            }
        }

        // Result
        Object result = null;
        if (pos < offset + length) {
            int resByte = data[pos++] & 0xFF;
            if (resByte == 0xC3) result = Boolean.TRUE;
            else if (resByte == 0xC2) result = Boolean.FALSE;
            else if (resByte == 0xC0) result = null;
            else if (resByte <= 0x7F) result = resByte;
            else if ((resByte & 0xE0) == 0xA0) {
                int strLen = resByte & 0x1F;
                int readLen = Math.min(strLen, (offset + length) - pos);
                result = new String(data, pos, readLen, StandardCharsets.UTF_8);
                pos += readLen;
            } else {
                result = "0x" + Integer.toHexString(resByte);
            }
        }

        return new Response(msgId, isSuccess, errorMsg, result);
    }
}
