/*
     This file is part of the LShift Java Library.

    The LShift Java Library is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    The LShift Java Library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The LShift Java Library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.lshift.java.lang;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.AccessibleObject;

import java.util.*;

import net.lshift.java.util.Bag;

public class EqualsHelper
{
    public static class EqualsHelperError
	extends Error
    {

        private static final long serialVersionUID = 1L;

        public EqualsHelperError(Throwable cause)
	{
	    super(cause);
	}
    }

    /**
     * Comapre two objects field by field.
     * @param a first object to compare
     * @param b the second object to compare
     * @param c We will test the fields defined in this class
     *  and all its super classes.
     */
    private static boolean equals(Object a, Object b, Class c, Equality equality)
    {
	if(c == Object.class) {
	    return true;
	}
	else {
	    Field [] fields = c.getDeclaredFields();
	    boolean result = true;
	    for(int i = 0; i != fields.length && result; ++i) {
		final Field field = fields[i];
		AccessibleObject.setAccessible(fields, true);
		if((field.getModifiers()&
		    (Modifier.TRANSIENT|Modifier.STATIC)) == 0) {
		    try {
			Class type = field.getType();
			Object fielda = field.get(a);
			Object fieldb = field.get(b);
			
			
			result =
			    (type.isPrimitive() 
                             ? fielda.equals(fieldb) 
                             : ((fielda == fieldb) || equality.equals(fielda, fieldb)));
		    }
		    catch(Exception e) {
			throw new EqualsHelperError(e);
		    }
		}
	    }
	    
	    return result && equals(a, b, c.getSuperclass(), equality);
	}
    }

    public static boolean equals(Object a, Object b, Equality e)
    {
        return (a == b) || (a != null && b != null 
                            && a.getClass() == b.getClass() 
                            && equals(a, b, a.getClass(), e));
    }

    /**
     * Check two lists are equal, given the provided
     * equality predicate.
     */
    public static boolean equals(List a, List b, Equality e)
    {
	boolean result = (a.size() == b.size());
	for(Iterator ai = a.iterator(), bi = b.iterator(); 
	    ai.hasNext() && bi.hasNext() && result;)
	    result = e.equals(ai.next(), bi.next());
	return result;
    }

    /**
     * Check two collections are equal, given the provided
     * equality predicate. This works for sets and bags, but not lists
     * The order of the items in the collection is not important.
     * This operation is O(N^2)
     */
    private static boolean unorderedEquals(Collection a, Collection b, Equality e)
    {
	if(a.size() == b.size()) {
	    Collection copy = new LinkedList(b);
	    boolean result = true;
	    for(Iterator ai = a.iterator(); result && ai.hasNext();) {
		result = false;
		Object itema = ai.next();
		for(Iterator bi = copy.iterator(); !result && bi.hasNext();) {
		    result = e.equals(itema, bi.next());
		    if(result) bi.remove();
		}
	    }

	    return result;
	}
	else {
	    return false;
	}
    }

    public static boolean equals(Set a, Set b, Equality e)
    {
        return unorderedEquals(a, b, e);
    }

    public static boolean equals(Bag a, Bag b, Equality e)
    {
        return unorderedEquals(a, b, e);
    }

    public static boolean equals(Map a, Map b, Equality equality)
    {
        boolean result = (a.size() == b.size());
        for(Iterator e = a.entrySet().iterator(); e.hasNext() && result;) {
            Map.Entry entry = (Map.Entry)e.next();
            result = b.containsKey(entry.getKey()) &&
                equality.equals(entry.getValue(), b.get(entry.getKey()));
        }

        return result;
    }

    /**
     * Equality using the following:
     * @return 
     *   when a and b are CharSequence - a.equals(b) 
     *   when a and b are Bag, Set - unorderCollectionEquals
     *   when a and b are List, SortedSet - orderedCollectionEquals
     *   when a and b are Map - use natural equality for keys,
     *     bug induction and reflection for values.
     *   default: a.class == b.class and fieldEquals(a, b, a.class).
     */
    public static final Equality INDUCTIVE_DEFAULT;
    static {
        InductiveEquality ie = new InductiveEquality();
        ie.setDelegate(DefaultEquality.equality(ie));
        INDUCTIVE_DEFAULT = ie;
    }

    public static final boolean equals(Object a, Object b)
    {
	return INDUCTIVE_DEFAULT.equals(a, b);
    }

}