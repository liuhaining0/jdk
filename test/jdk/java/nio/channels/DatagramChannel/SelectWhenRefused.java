/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* @test
 * @bug 6935563 7044870
 * @summary Test that Selector does not select an unconnected DatagramChannel when
 *    ICMP port unreachable received
 * @run main/othervm SelectWhenRefused
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.Set;

public class SelectWhenRefused {

    public static void main(String[] args) throws IOException {
        //If another datagram test interferes with this test ignoreStrayWakeup
        // will be set to true, and the loop below will retry the test
        boolean ignoreStrayWakeup;

        DatagramChannel dc1 = DatagramChannel.open().bind(new InetSocketAddress(0));
        int port = dc1.socket().getLocalPort();

        // datagram sent to this address should be refused
        SocketAddress refuser = new InetSocketAddress(InetAddress.getLocalHost(), port);

        DatagramChannel dc = DatagramChannel.open().bind(new InetSocketAddress(0));
        dc1.close();

        Selector sel = Selector.open();
        try {
            dc.configureBlocking(false);
            dc.register(sel, SelectionKey.OP_READ);

            for (;;) {
                /* Test 1: not connected so ICMP port unreachable should not be received */
                sendDatagram(dc, refuser);
                int n = sel.select(2000);
                if (n > 0) {
                    ignoreStrayWakeup = checkUnexpectedWakeup(dc, sel.selectedKeys());
                    sel.selectedKeys().clear();
                    if (ignoreStrayWakeup) {
                        continue;
                    }
                    // BindException will be thrown if another service is using
                    // our expected refuser port, cannot run just exit.
                    DatagramChannel.open().bind(refuser).close();
                    throw new RuntimeException("Unexpected wakeup");
                }

                /* Test 2: connected so ICMP port unreachable may be received */
                dc.connect(refuser);
                try {
                    sendDatagram(dc, refuser);
                    n = sel.select(2000);
                    if (n > 0) {
                        sel.selectedKeys().clear();
                        try {
                            ByteBuffer buf = ByteBuffer.allocate(100);
                            SocketAddress sa = dc.receive(buf);
                            String message = new String(buf.array());
                            System.out.format("received %s at %s from %s%n", message, dc.getLocalAddress(), sa);
                            //If any received data contains the message from sendDatagram then return true
                            if (message.contains("Greetings!")) {
                                throw new RuntimeException("Unexpected datagram received");
                            }
                            //unexpected datagram received from elsewhere, retry
                            continue;
                        } catch (PortUnreachableException pue) {
                            // expected
                        }
                    }
                } finally {
                    dc.disconnect();
                }

                /* Test 3: not connected so ICMP port unreachable should not be received */
                sendDatagram(dc, refuser);
                n = sel.select(2000);
                if (n > 0) {
                    ignoreStrayWakeup = checkUnexpectedWakeup(dc, sel.selectedKeys());
                    sel.selectedKeys().clear();
                    if (ignoreStrayWakeup) {
                        continue;
                    }
                    throw new RuntimeException("Unexpected wakeup after disconnect");
                }
                break;
            }
        } catch (BindException e) {
            // Do nothing, some other test has used this port
        } finally {
            sel.close();
            dc.close();
        }
    }

    static void sendDatagram(DatagramChannel dc, SocketAddress remote)
        throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap("Greetings!".getBytes());
        dc.send(bb, remote);
    }

    static boolean checkUnexpectedWakeup(DatagramChannel dc, Set<SelectionKey> selectedKeys) {
        for (SelectionKey key : selectedKeys) {
            if (!key.isValid() || !key.isReadable()) {
                System.out.println("Invalid or unreadable key: " + key);
                continue;
            }
            try {
                System.out.println("Attempting to read datagram from key: " + key);
                ByteBuffer buf = ByteBuffer.allocate(100);
                SocketAddress sa = dc.receive(buf);
                String message = new String(buf.array());
                System.out.format("received %s from %s%n", message, sa);
                //If any received data contains the message from sendDatagram then return true
                if (message.contains("Greetings!")) {
                    return true;
                }
            } catch (IOException io) {
                System.out.println("Unable to read from datagram " + io);
            }
        }
        return false;
    }
}
