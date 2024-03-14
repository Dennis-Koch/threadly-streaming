package org.threadlys.streams;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Allows to implement with Java Streams a consistent behavior with thrown
 * exceptions on distinct items - without interrupting the stream processing as
 * a whole.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 * @param <L>
 * @param <R>
 */
// CHECKSTYLE: DeclarationOrderCheck OFF
// CHECKSTYLE: IllegalCatch OFF
@SuppressWarnings({ "PMD.AvoidFieldNameMatchingMethodName", "PMD.AvoidInstanceofChecksInCatchClause", "PMD.AvoidReassigningCatchVariables", "checkstyle:DeclarationOrderCheck",
        "checkstyle:IllegalCatch" })
public final class Either<L, R> {
    /**
     * Wraps a given function into another function that returns a complex type
     * {@link Either} which contiously separates any error or exception into its
     * {@link #getLeft()} property and any successfully returned result from the
     * wrapped function its its {@link #getRight()} property.<br>
     * <br>
     * This is especially helpful in Java Streams API usescases where you dont want
     * a stream to terminate uncontrollably if any kind of exception happens on a
     * distinct items of the stream<br>
     * <br>
     * Please be aware that any <code>Either</code> instance has either its left
     * property (=function exception) populated or its right property (=function
     * result) - never both or none.
     *
     * @param <T>
     * @param <R>
     * @param function
     * @return
     */
    public static <T, R> Function<T, Either<Throwable, R>> lift(CheckedFunction<T, R> function) {
        return t -> {
            try {
                return Either.right(function.apply(t));
            } catch (InvocationTargetException e) {
                return Either.left(((InvocationTargetException) e).getCause());
            } catch (Throwable e) {
                return Either.left(e);
            }
        };
    }

    /**
     * Similar to {@link #lift(CheckedFunction)} this allows to wrap a given
     * function, but in addition also captures the function parameter on the
     * resulting {@link #getLeft()} {@link Pair} handle. This allows e.g. a Java
     * Streams chain to retry an invocation or to track which items exactly had a
     * faulty behavior.
     *
     * @param <T>
     * @param <R>
     * @param function
     * @return
     */
    public static <T, R> Function<T, Either<Pair<Throwable, T>, R>> liftWithValue(CheckedFunction<T, R> function) {
        return t -> {
            try {
                return Either.right(function.apply(t));
            } catch (InvocationTargetException e) {
                return Either.left(Pair.of(((InvocationTargetException) e).getCause(), t));
            } catch (Throwable e) {
                return Either.left(Pair.of(e, t));
            }
        };
    }

    /**
     * Allows to chain a function into a Java Stream where the upstream step was an
     * invocation of {@link #lift(CheckedFunction)}. It makes sure that the
     * downstream function is not invoked for a distinct item if the upstream
     * function had an exception during processing of this item.<br>
     * <br>
     * As a result a full stream execution can continue by skipping some of its
     * faulty iterations without escalating an exception uncontrollably via the
     * thread stack.
     *
     * @param <T>
     * @param <R>
     * @param function
     * @return
     */
    public static <T, R> Function<Either<Throwable, T>, Either<Throwable, R>> failFast(CheckedFunction<T, R> function) {
        Function<T, Either<Throwable, R>> liftedFunction = lift(function);
        return upstreamEither -> {
            if (upstreamEither.isLeft()) {
                // a prior call to a lifted function has an exception
                return Either.left(upstreamEither.getLeft().get());
            }
            return liftedFunction.apply(upstreamEither.getRight().get());
        };
    }

    private final L left;
    private final R right;

    private Either(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Creates an instance of <code>Either</code> where its left value is populated.
     * By convention such an instance describes and exceptional state.
     *
     * @param <L>
     * @param <R>
     * @param value
     * @return
     */
    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(value, null);
    }

    /**
     * Creates an instance of <code>Either</code> where its right value is
     * populated. By convention such an instance describes an normal,
     * non-exceptional state
     *
     * @param <L>
     * @param <R>
     * @param value
     * @return
     */
    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(null, value);
    }

    /**
     * Allows to wrap the left value into an optional structure
     *
     * @return
     */
    public Optional<L> getLeft() {
        return Optional.ofNullable(left);
    }

    /**
     * Allows to wrap the right value into an optional structure
     *
     * @return
     */
    public Optional<R> getRight() {
        return Optional.ofNullable(right);
    }

    /**
     * True if the enclosing state has the left value populated
     *
     * @return
     */
    public boolean isLeft() {
        return left != null;
    }

    /**
     * True if the enclosing state has the right value populated
     *
     * @return
     */
    public boolean isRight() {
        return right != null;
    }

    /**
     * Allows to chain closures with using an <code>Either</code> instance. In this
     * case a given function is invoked and its result returned if - and only if -
     * the current instance has its left state populated
     *
     * @param <T>
     * @param mapper
     * @return
     */
    public <T> Optional<T> mapLeft(Function<? super L, T> mapper) {
        if (isLeft()) {
            return Optional.of(mapper.apply(left));
        }
        return Optional.empty();
    }

    /**
     * Allows to chain closures with using an <code>Either</code> instance. In this
     * case a given function is invoked and its result returned if - and only if -
     * the current instance has its right state populated
     *
     * @param <T>
     * @param mapper
     * @return
     */
    public <T> Optional<T> mapRight(Function<? super R, T> mapper) {
        if (isRight()) {
            return Optional.of(mapper.apply(right));
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        if (isLeft()) {
            return "Left(" + left + ")";
        }
        return "Right(" + right + ")";
    }
}
