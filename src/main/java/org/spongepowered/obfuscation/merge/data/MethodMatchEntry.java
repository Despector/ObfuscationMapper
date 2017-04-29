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
package org.spongepowered.obfuscation.merge.data;

import org.spongepowered.despector.ast.type.MethodEntry;

import java.util.HashMap;
import java.util.Map;

public class MethodMatchEntry {

    private final MethodEntry old_mth;
    private MethodEntry new_mth;
    private boolean merged = false;

    private Map<MethodEntry, Integer> votes = new HashMap<>();

    public MethodMatchEntry(MethodEntry old) {
        this.old_mth = old;
    }

    public MethodEntry getOldMethod() {
        return this.old_mth;
    }

    public MethodEntry getNewMethod() {
        return this.new_mth;
    }

    public boolean isMerged() {
        return this.merged;
    }

    public void setMerged() {
        this.merged = true;
    }

    public void setNewMethod(MethodEntry type) {
        this.new_mth = type;
    }

    public boolean vote(MethodEntry n) {
        if (this.new_mth != null) {
            return false;
        }
        Integer v = this.votes.get(n);
        if (v != null) {
            this.votes.put(n, v + 1);
        } else {
            this.votes.put(n, 1);
        }
        return true;
    }

    public Map<MethodEntry, Integer> getVotes() {
        return this.votes;
    }

}
