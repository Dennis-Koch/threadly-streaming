package org.threadlys.threading;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Marker interface used by {@link ContextSnapshot} as well as
 * {@link ContextSnapshotFactory} to conveniently encapsulate the logic whether
 * a stream is really configured to run in parallel on application level or not
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface ParallelStreamFassade {
    /**
     * Encapsulates the conditional logic whether really parallel stream processing
     * is activated at runtime. With this method you can implement parallel stream
     * processing that is at runtime optionally still running sequentially. This is
     * extremely helpful if you have any runtime issues with your service and you
     * are not sure whether it might be caused by a race-condition or not. So by
     * simply configured a global concurrency flag you can probe your parallel code
     * in sequential mode without changing it use everywhere in multiple
     * classes.<br>
     * <br>
     * It is meant to be used as a replacement to
     * {@link Collection#parallelStream()} and {@link Stream#parallel()} in order to
     * allow the conditional logic to be applied dynamically.<br>
     * <br>
     * Examples:<br>
     * <br>
     * Instead of:<br>
     * <code>List<Integer> myCollection = ...<br>myCollection.parallelStream()<br>&nbsp;&nbsp;.map(cs.scoped(...))<br>&nbsp;&nbsp;.collect(...)</code><br>
     * <br>
     * You write:<br>
     * <code>List<Integer> myCollection = ...<br>cs.parallel(myCollection.stream())<br>&nbsp;&nbsp;.map(cs.scoped(...))<br>&nbsp;&nbsp;.collect(...)</code>
     *
     * @param <T>
     * @param stream
     * @return
     */
    <T> Stream<T> parallel(Stream<T> stream);

    /**
     * Convenience method compared to {@link #parallel(Stream)}.<br>
     * <br>
     * Instead of:<br>
     * <code>List<Integer> myCollection = ...<br>cs.parallel(myCollection.stream())<br>&nbsp;&nbsp;.map(cs.scoped(...))<br>&nbsp;&nbsp;.collect(...)</code><br>
     * <br>
     * You can write:<br>
     * <code>List<Integer> myCollection = ...<br>cs.parallelStream(myCollection)<br>&nbsp;&nbsp;.map(cs.scoped(...))<br>&nbsp;&nbsp;.collect(...)</code>
     *
     * @param <T>
     * @param collection
     * @return
     */
    <T> Stream<T> parallel(Collection<T> collection);
}
