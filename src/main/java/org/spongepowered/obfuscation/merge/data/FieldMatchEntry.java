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

import static com.google.common.base.Preconditions.checkNotNull;

import org.spongepowered.despector.ast.type.FieldEntry;

import java.util.HashMap;
import java.util.Map;

public class FieldMatchEntry {

    private final FieldEntry old_field;
    private FieldEntry new_field;
    private boolean merged = false;

    private int highest = 0;
    private FieldEntry highest_type = null;
    private int second = 0;
    private Map<FieldEntry, Integer> votes = new HashMap<>();

    public FieldMatchEntry(FieldEntry old) {
        this.old_field = checkNotNull(old, "old");
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
        this.votes = null;
    }

    public boolean vote(FieldEntry n) {
        if (this.new_field != null) {
            return false;
        }
        if (this.old_field.isStatic() ^ n.isStatic()) {
            return false;
        }
        Integer v = this.votes.get(n);
        if (v != null) {
            int vote = v + 1;
            this.votes.put(n, vote);
            if (vote > this.highest) {
                if (n == this.highest_type) {
                    this.highest = vote;
                } else {
                    this.second = this.highest;
                    this.highest = vote;
                    this.highest_type = n;
                }
            } else if (vote > this.second) {
                this.second = vote;
            }
        } else {
            this.votes.put(n, 1);
            if (this.highest == 0) {
                this.highest = 1;
                this.highest_type = n;
            }
        }
        return true;
    }

    public Map<FieldEntry, Integer> getVotes() {
        return this.votes;
    }

    public int getHighestVote() {
        return this.highest;
    }

    public FieldEntry getHighest() {
        return this.highest_type;
    }

    public int getVoteDifference() {
        return this.highest - this.second;
    }

    public void removeVote(FieldEntry n) {
        this.votes.remove(n);
        this.second = 0;
        this.highest = 0;
        this.highest_type = null;
        for (Map.Entry<FieldEntry, Integer> e : this.votes.entrySet()) {
            if (e.getValue() > this.highest) {
                this.second = this.highest;
                this.highest = e.getValue();
                this.highest_type = e.getKey();
            } else if (e.getValue() > this.second) {
                this.second = e.getValue();
            }
        }
    }

}
