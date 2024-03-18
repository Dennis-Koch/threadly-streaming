package org.threadlys.threading.test.context;

import java.util.Collection;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface TestService {
    int testOperation(String arg);

    Collection<Integer> testOperationBatch(Collection<String> items);

    boolean isClear();
}
