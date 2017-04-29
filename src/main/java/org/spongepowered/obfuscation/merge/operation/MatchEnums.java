/*
 * The MIT License (MIT)
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.obfuscation.merge.operation;

import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;

public class MatchEnums implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {
        for (EnumEntry n : set.getNewSourceSet().getAllEnums()) {
            if (n.isAnonType() || n.getEnumConstants().isEmpty()) {
                continue;
            }
            boolean is_inner = n.getName().contains("$");

            search: for (EnumEntry m : set.getOldSourceSet().getAllEnums()) {
                if (m.isAnonType() || m.getEnumConstants().isEmpty()) {
                    continue;
                }
                if (n.getEnumConstants().size() < m.getEnumConstants().size()) {
                    continue;
                }
                if (m.getName().contains("$") ^ is_inner) {
                    continue;
                }
                for (String cst : m.getEnumConstants()) {
                    if (!n.getEnumConstants().contains(cst)) {
                        continue search;
                    }
                }
                set.getMatch(m).vote(n);
            }
        }

    }

}
