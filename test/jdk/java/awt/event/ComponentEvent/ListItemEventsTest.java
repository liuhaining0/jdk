/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @key headful
 * @bug 8295774
 * @summary Verify that List Item selection via mouse/keys generates ItemEvent/ActionEvent appropriately.
 * @run main ListItemEventsTest
 */
public class ListItemEventsTest {

    private static final int waitDelay = 1000;

    private volatile static Frame frame;
    private volatile static List list;
    private volatile static boolean actionPerformed = false;
    private volatile static boolean itemStateChanged = false;
    private volatile static Robot robot;
    private volatile static CountDownLatch latch;

    public static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        list.add("Four");
        list.add("Five");
        list.addItemListener((event) -> {
            System.out.println("Got an ItemEvent:" + event);
            itemStateChanged = true;
        });
        list.addActionListener((event) -> {
            System.out.println("Got an ActionEvent:" + event);
            actionPerformed = true;
        });
        list.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent event) {
                System.out.println("Got an FocusEvent:" + event);
                latch.countDown();
            }
        });

        frame.add(list);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] s) throws Exception {
        latch = new CountDownLatch(1);
        robot = new Robot();
        try {

            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeLater(ListItemEventsTest::initializeGUI);
            robot.waitForIdle();

            Point listAt = list.getLocationOnScreen();
            // get bounds of button
            Rectangle bounds = list.getBounds();

            robot.mouseMove(listAt.x + bounds.width / 2,
                listAt.y + bounds.height / 2);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                    "Fail: Timed out waiting for list to gain focus, test cannot proceed!!");
            }

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected!");
            }

            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            if (!actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ActionEvent when double"
                        + " clicked!");
            }

            itemStateChanged = false;
            actionPerformed = false;

            EventQueue.invokeAndWait(() -> list.select(0));

            if (itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List triggered ItemEvent when item selected by "
                        + "calling the API!");
            }

            robot.setAutoDelay(waitDelay);

            itemStateChanged = false;
            keyType(KeyEvent.VK_DOWN);

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected by"
                        + " down arrow key!");
            }

            itemStateChanged = false;
            keyType(KeyEvent.VK_UP);

            if (!itemStateChanged) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ItemEvent when item selected by"
                        + " up arrow key!");
            }

            if (actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List triggerd ActionEvent unnecessarily. Action generated"
                        + " when item selected using API or UP/DOWN keys!");
            }

            actionPerformed = false;
            keyType(KeyEvent.VK_ENTER);

            if (!actionPerformed) {
                throw new RuntimeException(
                    "FAIL: List did not trigger ActionEvent when enter"
                        + " key typed!");
            }

            System.out.println("Test passed!");

        } finally {
            EventQueue.invokeAndWait(ListItemEventsTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static void keyType(int key) throws Exception {
        robot.keyPress(key);
        robot.keyRelease(key);
    }
}
