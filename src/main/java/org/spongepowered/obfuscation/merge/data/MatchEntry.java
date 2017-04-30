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

import org.spongepowered.despector.ast.type.TypeEntry;

import java.util.HashMap;
import java.util.Map;

public class MatchEntry {

    private final TypeEntry old_type;
    private TypeEntry new_type;
    private boolean merged = false;

    private int highest = 0;
    private TypeEntry highest_type = null;
    private int second = 0;
    private Map<TypeEntry, Integer> votes = new HashMap<>();

    public MatchEntry(TypeEntry old) {
        this.old_type = checkNotNull(old, "old");
    }

    public TypeEntry getOldType() {
        return this.old_type;
    }

    public TypeEntry getNewType() {
        return this.new_type;
    }

    public void setNewType(TypeEntry type) {
        this.new_type = type;
        this.votes = null;
    }

    public boolean isMerged() {
        return this.merged;
    }

    public void setAsMerged() {
        this.merged = true;
    }

    public boolean vote(TypeEntry n) {
        if (this.new_type != null) {
            return this.new_type == n;
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

    public Map<TypeEntry, Integer> getVotes() {
        return this.votes;
    }

    public int getHighestVote() {
        return this.highest;
    }

    public TypeEntry getHighest() {
        return this.highest_type;
    }

    public int getVoteDifference() {
        return this.highest - this.second;
    }

    public void removeVote(TypeEntry n) {
        this.votes.remove(n);
        this.second = 0;
        this.highest = 0;
        this.highest_type = null;
        for (Map.Entry<TypeEntry, Integer> e : this.votes.entrySet()) {
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
