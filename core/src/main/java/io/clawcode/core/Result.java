package io.clawcode.core;

import java.util.function.Function;

public sealed interface Result<T, E> {

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok<T, E>;
    }

    default boolean isErr() {
        return this instanceof Err<T, E>;
    }

    default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
        return switch (this) {
            case Ok<T, E> ok -> new Ok<>(mapper.apply(ok.value));
            case Err<T, E> err -> new Err<>(err.error);
        };
    }

    record Ok<T, E>(T value) implements Result<T, E> {}

    record Err<T, E>(E error) implements Result<T, E> {}
}
