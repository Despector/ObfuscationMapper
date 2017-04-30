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

import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.HashSet;
import java.util.Set;

public class MergeInitializers implements MergeOperation {

    private Set<String> handled = new HashSet<>();

    @Override
    public void operate(MergeEngine set) {

        for (MatchEntry match : set.getAllMatches()) {
            if (match.getNewType() == null || this.handled.contains(match.getNewType().getName())) {
                continue;
            }
            this.handled.add(match.getNewType().getName());

            MethodEntry old_clinit = match.getOldType().getStaticMethod("<clinit>");
            MethodEntry new_clinit = match.getNewType().getStaticMethod("<clinit>");
            if (old_clinit != null && new_clinit != null) {
                MethodMatchEntry m = set.getPendingMethodMatch(old_clinit);
                m.setNewMethod(new_clinit);
                set.setAsMatched(m);
            }
        }
    }

}
