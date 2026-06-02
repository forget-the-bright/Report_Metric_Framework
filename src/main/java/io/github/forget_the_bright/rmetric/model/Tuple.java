package io.github.forget_the_bright.rmetric.model;

import java.util.Objects;


/**
 * 二元组数据结构，用于封装两个相关联的对象。
 *
 * <p>该类常用于需要返回多个值或临时组合两个对象的场景。</p>
 *
 * @param <T> 第一个元素的类型
 * @param <U> 第二个元素的类型
 * @author wanghao (helloworlwh@163.com)
 * @since 2024/11/13 下午4:44
 */
public class Tuple<T, U> {

    private T first;
    private U second;

    public Tuple() {

    }

    public static <T, U> Tuple<T, U> newTuple(T first, U second) {
        return new Tuple<>(first, second);
    }

    public Tuple(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public void setFirst(T first) {
        this.first = first;
    }

    public U getSecond() {
        return second;
    }

    public void setSecond(U second) {
        this.second = second;
    }

    @Override
    public String toString() {
        return "Tuple{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(first, tuple.first) && Objects.equals(second, tuple.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}
