package com.lrenyi.template.flow;

import java.util.Set;

/**
 * 测试用 Joiner：配对场景下对指定 key 的 isMatched 返回 false，用于 IT_CAFFEINE_MISMATCH。
 */
public class MismatchPairingJoiner extends PairingJoiner {

    private final Set<String> mismatchKeys;

    public MismatchPairingJoiner(Set<String> mismatchKeys) {
        this.mismatchKeys = mismatchKeys != null ? Set.copyOf(mismatchKeys) : Set.of();
    }

    @Override
    public boolean isMatched(PairItem existing, PairItem incoming) {
        return !mismatchKeys.contains(existing.getId());
    }
}
