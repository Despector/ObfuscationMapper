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

import org.spongepowered.despector.ast.type.TypeEntry;

import java.util.HashMap;
import java.util.Map;

public class MatchEntry {

    private final TypeEntry old_type;
    private TypeEntry new_type;

    private Map<TypeEntry, Integer> votes = new HashMap<>();

    public MatchEntry(TypeEntry old) {
        this.old_type = old;
    }

    public TypeEntry getOldType() {
        return this.old_type;
    }

    public TypeEntry getNewType() {
        return this.new_type;
    }

    public void setNewType(TypeEntry type) {
        this.new_type = type;
    }

    public boolean vote(TypeEntry n) {
        if (this.new_type != null) {
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

    public Map<TypeEntry, Integer> getVotes() {
        return this.votes;
    }

}
