package org.threadlys.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.threadlys.streams.CheckedCallable;
import org.threadlys.streams.CheckedConsumer;
import org.threadlys.streams.CheckedFunction;
import org.threadlys.streams.CheckedPredicate;
import org.threadlys.streams.CheckedRunnable;
import org.threadlys.streams.CheckedSupplier;
import org.threadlys.utils.IStateRevert;

/**
 * A handle containing a snapshot of values of thread-local fields at the moment of calling {@link ContextSnapshotFactory#createSnapshot()}.<br>
 * <br>
 * This handle can be used either
 * <ol>
 * <li>for Java Streams API - then with the {@link #push()} and {@link #pop()} operations</li></li>or for wrapping Runnables / Callables for an {@link Executor} or {@link ForkJoinPool}</li>
 * <li>or just manually with a try/finally pattern with the {@link #apply(IStateRevert...)} operations</li>
 * </ol>
 * <br>
 * <br>
 *
 * Examples:<br>
 * Assuming you have a simple transferrable thread-local declared like this:<br>
 * <br>
 * <code>public class MyBean {<br>
 * &nbsp;&#64;Transferrable<br>
 * &nbsp;private ThreadLocal<Integer> valueTL = new NamedThreadLocal<>("MyBean");<br>
 * }<br></code><br>
 * Then you can make use of a transferred value in worker threads like this - correlated to the 3 usage options mentioned above:<br>
 * <br>
 * <code>
 * List&lt;Integer&gt; myList = ...<br>
 * ContextSnapshotFactory csf = ...<br>
 * ContextSnapshot cs = csf.createSnapshot();<br>
 * </code><br>
 *
 * <ol>
 * <li><code>List&lt;Integer&gt; streamResult =
 * myList.stream()<br>
 * &nbsp;&nbsp;.parallel()<br>
 * &nbsp;&nbsp;.map(cs.push())<br>
 * &nbsp;&nbsp;.map(item -> {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;// do some calculation stuff that uses valueTL.get() from MyBean<br>
 * &nbsp;&nbsp;})<br>
 * &nbsp;&nbsp;.map(cs.pop())<br>
 * &nbsp;&nbsp;.collect(Collectors.toList());
 * </code></li>
 * <li><code>executor.run(cs.scoped(() -> {<br>
 * &nbsp;&nbsp;// do some calculation stuff that uses valueTL.get() from MyBean<br>
 * }));<br>
 * </code></li>
 * <li><code>// executed any worker thread<br>
 * IStateRevert revert = cs.apply();<br>
 * try {<br>
 * &nbsp;&nbsp;// do some calculation stuff that uses valueTL.get() from MyBean<br>
 * } finally {<br>
 * &nbsp;&nbsp;revert.revert();<br>
 * }<br>
 * </code></li>
 * </ol>
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface ContextSnapshot extends ParallelStreamFassade {
    /**
     * Used together with Java Streams API when using parallel streams and requiring parallelism on multiple stream stages. You should use it also together with a {@link #pop()} at the end of your
     * parallel stream to take care of thread-local memory leaks. If you only need parallelism it on a single stream stage it is recommended to use one of the {@link #scoped(CheckedFunction)}
     * overloads instead for increased robustness against stream exceptions.
     *
     * @param <T>
     * @return
     */
    <T> Function<T, T> push();

    /**
     * It is after you parallel stream stage has finished.
     *
     * @param <T>
     * @return
     */
    <T> Function<T, T> pop();

    /**
     * Allows fine-grained control of when exactly and for how long a context snapshot shall be applied to the current thread. In most circumstances on of the {@link #scoped(CheckedFunction)}
     * overloads is the preferred methods as it takes automatically care about a proper try/finally structure internally for robustness against thread-local leaks.
     *
     * @return The revert handle that allows to revert all applied changes by simply invoking {@link IStateRevert#revert()}
     */
    IStateRevert apply();

    /**
     * Allows fine-grained control of when exactly and for how long a context snapshot shall be applied to the current thread. In most circumstances on of the {@link #scoped(CheckedFunction)}
     * overloads is the preferred methods as it takes automatically care about a proper try/finally structure internally for robustness against thread-local leaks.
     *
     * @param reverts
     *            One or more reverts to chain in into the returned handle
     * @return The revert handle that allows to revert all applied changes by simply invoking {@link IStateRevert#revert()}
     */
    IStateRevert apply(IStateRevert... reverts);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given runnable. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;runnable.run();<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param runnable
     *            The runnable to wrap into another runnable that does maintain a try/finally structure for applying the current context
     * @return
     */
    Runnable scoped(CheckedRunnable runnable);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given function. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;return function.apply(arg);<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param <T>
     * @param <R>
     * @param function
     *            The function to wrap into another function that does maintain a try/finally structure for applying the current context
     * @return
     */
    <T, R> Function<T, R> scoped(CheckedFunction<T, R> function);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given supplier. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;return supplier.get();<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param <R>
     * @param supplier
     *            The supplier to wrap into another supplier that does maintain a try/finally structure for applying the current context
     * @return
     */
    <R> Supplier<R> scoped(CheckedSupplier<R> supplier);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given callable. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;return callable.call();<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param <T>
     * @param callable
     *            The callable to wrap into another callable that does maintain a try/finally structure for applying the current context
     * @return
     */
    <T> Callable<T> scopedCallable(CheckedCallable<T> callable);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given consumer. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;consumer.accept(arg);<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param <T>
     * @param consumer
     *            The consumer to wrap into another consumer that does maintain a try/finally structure for applying the current context
     * @return
     */
    <T> Consumer<T> scopedConsumer(CheckedConsumer<T> consumer);

    /**
     * Convenience method to apply the current snapshot for exactly the duration of the execution of the given predicate. Calling this method is equivalent to:<br>
     * <br>
     * <code>IStateRevert revert = cs.apply();<br>
     * try {<br>
     * &nbsp;&nbsp;predicate.accept(arg);<br>
     * } finally {<br>
     * &nbsp;&nbsp;revert.revert();<br>
     * }</code>
     *
     * @param <T>
     * @param predicate
     *            The predicate to wrap into another predicate that does maintain a try/finally structure for applying the current context
     * @return
     */
    <T> Predicate<T> scopedPredicate(CheckedPredicate<T> predicate);
}
