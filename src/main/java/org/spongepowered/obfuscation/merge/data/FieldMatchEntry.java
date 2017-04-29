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

import org.spongepowered.despector.ast.type.FieldEntry;

import java.util.HashMap;
import java.util.Map;

public class FieldMatchEntry {

    private final FieldEntry old_field;
    private FieldEntry new_field;
    private boolean merged = false;

    private Map<FieldEntry, Integer> votes = new HashMap<>();

    public FieldMatchEntry(FieldEntry old) {
        this.old_field = old;
    }

    public FieldEntry getOldField() {
        return this.old_field;
    }

    public FieldEntry getNewField() {
        return this.new_field;
    }

    public boolean isMerged() {
        return this.merged;
    }

    public void setMerged() {
        this.merged = true;
    }

    public void setNewField(FieldEntry type) {
        this.new_field = type;
    }

    public boolean vote(FieldEntry n) {
        if (this.new_field != null) {
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

    public Map<FieldEntry, Integer> getVotes() {
        return this.votes;
    }

}
