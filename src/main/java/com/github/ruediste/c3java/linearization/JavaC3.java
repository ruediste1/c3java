package com.github.ruediste.c3java.linearization;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implement C3 Linearization
 * http://www.webcom.com/haahr/dylan/linearization-oopsla96.html
 * 
 * This is a translation of the dylan example at the end of the above paper.
 * 
 * @author david
 */
public class JavaC3 {
    private static class LinearizationKey {
        public DirectSuperclassesInspector directParentClassesReader;
        public Class<?> type;

        public LinearizationKey(DirectSuperclassesInspector directParentClassesReader, Class<?> type) {
            this.directParentClassesReader = directParentClassesReader;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                // you wouldn't think this could happen, but there
                // is no way to stop garbage collection from happening
                // on linearizations during a get(), or put(), so we
                // must check this is not null.
                return false;
            } else {
                LinearizationKey other = (LinearizationKey) o;
                return type.equals(other.type) && directParentClassesReader.equals(other.directParentClassesReader);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(type, directParentClassesReader);
        }
    }

    private static Map<LinearizationKey, Iterable<Class<?>>> linearizations = java.util.Collections
            .synchronizedMap(new WeakHashMap<LinearizationKey, Iterable<Class<?>>>());

    /**
     * Thrown when its not possible to linearize all superclasses.
     */
    public static class JavaC3Exception extends Error {
        private static final long serialVersionUID = 1L;
        private final Iterable<Class<?>> partialResult;
        private final Iterable<List<Class<?>>> remainingInputs;
        private final DirectSuperclassesInspector dsc;

        protected JavaC3Exception(DirectSuperclassesInspector dsc, Iterable<Class<?>> partialResult,
                Iterable<List<Class<?>>> remainingInputs) {
            super("inconsistent precedence");
            this.dsc = dsc;
            this.partialResult = partialResult;
            this.remainingInputs = remainingInputs;
        }

        /**
         * Gets the value of partialResult This is really for expert use only.
         * Its the value of partialResult at the point the linearization failed.
         * 
         * @return the value of partialResult
         */
        public Iterable<Class<?>> getPartialResult() {
            return this.partialResult;
        }

        /**
         * Gets the value of remainingInputs This is really for expert use only.
         * Its the value of remaining inputs at the point the linearization
         * failed.
         * 
         * @return the value of remainingInputs
         */
        public Iterable<List<Class<?>>> getRemainingInputs() {
            return this.remainingInputs;
        }

        @Override
        public String toString() {
            List<String> superclasses = Lists.newArrayListWithCapacity(Iterables.size(partialResult));
            for (Class<?> c : partialResult) {
                superclasses.add(MessageFormat.format("    {0}: {1}", c, dsc.directParentClasses(c)));
            }
            return MessageFormat.format("inconsistent precendence:\nsuperclasses:\n {0}\nremaining:\n   {1}",
                    Joiner.on("\n").join(superclasses, "\n"), remainingInputs);
        }
    }

    private static Iterable<Class<?>> mergeLists(List<Class<?>> partialResult,
            final List<List<Class<?>>> remainingInputs, final DirectSuperclassesInspector directParentClassesReader)
            throws JavaC3Exception {
        if (all(remainingInputs, equalTo(Collections.<Class<?>> emptyList()))) {
            return partialResult;
        }

        Optional<Class<?>> nextOption = Optional.absent();
        for (Class<?> c : Lists.reverse(partialResult)) {
            nextOption = Iterables.tryFind(directParentClassesReader.directParentClasses(c),
                    isCandidate(remainingInputs));
            if (nextOption.isPresent())
                break;
        }

        if (nextOption.isPresent()) {
            List<List<Class<?>>> newRemainingInputs = Lists.newArrayList();
            Class<?> next = nextOption.get();
            for (List<Class<?>> input : remainingInputs) {
                newRemainingInputs.add(input.indexOf(next) == 0 ? input.subList(1, input.size()) : input);
            }

            return mergeLists(newArrayList(concat(partialResult, singletonList(next))), newRemainingInputs,
                    directParentClassesReader);
        } else {
            throw new JavaC3Exception(directParentClassesReader, partialResult, remainingInputs);
        }
    }

    /**
     * To be a candidate for the next place in the linearization, you must be
     * the head of at least one list, and in the tail of none of the lists.
     * 
     * @param remainingInputs
     *            the lists we are looking for position in.
     * @return true if the class is a candidate for next.
     */
    private static <X> Predicate<X> isCandidate(final Iterable<List<X>> remainingInputs) {
        return new Predicate<X>() {

            Predicate<List<X>> headIs(final X c) {
                return new Predicate<List<X>>() {
                    @Override
                    public boolean apply(List<X> input) {
                        return !input.isEmpty() && c.equals(input.get(0));
                    }
                };
            }

            Predicate<List<X>> tailContains(final X c) {
                return new Predicate<List<X>>() {
                    @Override
                    public boolean apply(List<X> input) {
                        return input.indexOf(c) > 0;
                    }
                };
            }

            @Override
            public boolean apply(final X c) {
                return any(remainingInputs, headIs(c)) && all(remainingInputs, not(tailContains(c)));
            }
        };
    }

    private static Iterable<Class<?>> computeClassLinearization(Class<?> c, final DirectSuperclassesInspector dsc)
            throws JavaC3Exception {
        List<Class<?>> cDirectSuperclasses = dsc.directParentClasses(c);

        Function<Class<?>, List<Class<?>>> cplList = new Function<Class<?>, List<Class<?>>>() {
            @Override
            public List<Class<?>> apply(Class<?> c) {
                return newArrayList(allSuperclasses(c, dsc));
            }
        };

        return mergeLists(Collections.<Class<?>> singletonList(c),
                newArrayList(concat(Lists.transform(cDirectSuperclasses, cplList), singletonList(cDirectSuperclasses))),
                dsc);
    }

    /**
     * Return the linearization of c, using the
     * {@link DefaultDirectSuperclassesInspector}. The returned iterable will
     * start with c, followed by the superclasses of c in linearization order.
     */
    public static Iterable<Class<?>> allSuperclasses(Class<?> c) throws JavaC3Exception {
        return allSuperclasses(c, DefaultDirectSuperclassesInspector.INSTANCE);
    }

    /**
     * Return the linearization of c, using the
     * {@link DefaultDirectSuperclassesInspector}. The returned iterable will
     * start with {@link Object}, followed by the superclasses of c in
     * linearization order and end with c.
     */
    public static Collection<Class<?>> allSuperclassesReverse(Class<?> c) throws JavaC3Exception {
        ArrayList<Class<?>> result = new ArrayList<>();
        allSuperclasses(c, DefaultDirectSuperclassesInspector.INSTANCE).forEach(result::add);
        Collections.reverse(result);
        return result;
    }

    /**
     * Return the linearization of c. The returned iterable will start with c,
     * followed by the superclasses of c in linearization order.
     */
    public static Iterable<Class<?>> allSuperclasses(Class<?> c, DirectSuperclassesInspector directParentClassesReader)
            throws JavaC3Exception {
        LinearizationKey key = new LinearizationKey(directParentClassesReader, c);
        Iterable<Class<?>> linearization = linearizations.get(key);
        if (linearization == null) {
            linearization = computeClassLinearization(c, directParentClassesReader);
            linearizations.put(key, linearization);
        }

        return linearization;
    }

    /**
     * The class linearizations are cached in a static map. This cache can lead
     * to classloader leaks. By calling this class, the cache is flushed.
     */
    public static void clearCache() {
        linearizations.clear();
    }
}
