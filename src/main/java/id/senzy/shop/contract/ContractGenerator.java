package id.senzy.shop.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Memilih N kandidat acak dari pool tanpa pengulangan. Target & reward SELALU dari config. */
public final class ContractGenerator {

    public List<ContractPoolEntry> pick(List<ContractPoolEntry> pool, int count) {
        List<ContractPoolEntry> copy = new ArrayList<>(pool);
        List<ContractPoolEntry> chosen = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int n = Math.min(count, copy.size());
        for (int i = 0; i < n; i++) {
            int idx = random.nextInt(copy.size());
            chosen.add(copy.remove(idx));
        }
        return chosen;
    }
}
