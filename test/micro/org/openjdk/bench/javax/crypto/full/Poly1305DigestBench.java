/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.javax.crypto.full;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Poly1305DigestBench extends CryptoBase {
    public static final int SET_SIZE = 128;

    @Param({"64", "256", "1024", "" + 16*1024, "" + 1024*1024})
    int dataSize;

    private byte[][] data;
    int index = 0;
    private static MethodHandle polyEngineInit, polyEngineUpdate, polyEngineFinal;
    private static Object polyObj;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> polyClazz = Class.forName("com.sun.crypto.provider.Poly1305");
            Constructor<?> constructor = polyClazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            polyObj = constructor.newInstance();

            Method m = polyClazz.getDeclaredMethod("engineInit", Key.class, AlgorithmParameterSpec.class);
            m.setAccessible(true);
            polyEngineInit = lookup.unreflect(m);

            m = polyClazz.getDeclaredMethod("engineUpdate", byte[].class, int.class, int.class);
            m.setAccessible(true);
            polyEngineUpdate = lookup.unreflect(m);

            m = polyClazz.getDeclaredMethod("engineDoFinal");
            m.setAccessible(true);
            polyEngineFinal = lookup.unreflect(m);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    @Setup
    public void setup() {
        setupProvider();
        data = fillRandom(new byte[SET_SIZE][dataSize]);
    }

    @Benchmark
    public byte[] digest() {
        try {
            byte[] d = data[index];
            index = (index +1) % SET_SIZE;
            polyEngineInit.invoke(polyObj, new SecretKeySpec(d, 0, 32, "Poly1305"), null);
            polyEngineUpdate.invoke(polyObj, d, 0, d.length);
            return (byte[])polyEngineFinal.invoke(polyObj);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
