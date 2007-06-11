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

package net.lshift.java.dispatch;

import java.util.*;
import java.lang.reflect.*;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class DynamicDispatchTest
    extends TestCase
{
    public static TestSuite suite()
    {
        return new TestSuite(DynamicDispatchTest.class);
    }

    public interface X { }

    public static class A { }
    public static class B extends A { }
    public static class C extends A { }
    public static class D extends A { }
    public static class E extends A implements X { }

    public static class ToStringABC
    {
	public String toString(A a)
	{
	    return "A";
	}

	public String toString(B b)
	{
	    return "B";
	}

	public String toString(C c)
	{
	    return "C";
	}

	public String toString(X c)
	{
	    return "X";
	}

	public String toString(B b, C c)
	{
	    return "BC";
	}

	public String toString(C c, B b)
	{
	    return "CB";
	}

	public String toString(A a, int i)
	{
	    return "A"+ i;
	}
    }

    public interface ToString
    {
	public String toString(Object o);
	public String toString(Object o1, Object o2);
	public String toString(Object o, int i);
    }

    private Method toStringProcedure()
	throws Exception
    {
	return ToString.class.getDeclaredMethod
	    ("toString", new Class [] { Object.class });
    }

    private Method toString2Procedure()
	throws Exception
    {
	return ToString.class.getDeclaredMethod
	    ("toString", new Class [] { Object.class, Object.class });
    }

    private Method toStringMethod(Class c)
	throws Exception
    {
	return ToStringABC.class.getDeclaredMethod
	    ("toString", new Class [] { c });
    }

    private Method toStringMethod(Class c1, Class c2)
	throws Exception
    {
	return ToStringABC.class.getDeclaredMethod
	    ("toString", new Class [] { c1, c2 });
    }

    public void testAppliesTo()
	throws Exception
    {
	assertTrue("procedure applies to argument types",
		   DynamicDispatch.appliesTo
		   (toStringProcedure(), new Class [] { A.class }));
	assertTrue("procedure applies to method",
		   DynamicDispatch.appliesTo
		   (toStringProcedure(), toStringMethod(A.class)));
    }

    public void testProcedureMethods()
	throws Exception
    {
	Set methods = DynamicDispatch.procedureMethods
	    (toStringProcedure(), ToStringABC.class.getDeclaredMethods());
	assertEquals(4, methods.size());
	assertTrue("contains A", methods.contains(toStringMethod(A.class)));
	assertTrue("contains B", methods.contains(toStringMethod(B.class)));
	assertTrue("contains C", methods.contains(toStringMethod(C.class)));
	assertTrue("contains X", methods.contains(toStringMethod(X.class)));

	methods = DynamicDispatch.procedureMethods
	    (toString2Procedure(), ToStringABC.class.getDeclaredMethods());
	assertEquals(2, methods.size());
	assertTrue("contains BC", methods.contains(toStringMethod(B.class, C.class)));
	assertTrue("contains CB", methods.contains(toStringMethod(C.class, B.class)));
    }

    public void testToStringABC()
    {
	ToString x = (ToString)DynamicDispatch.proxy(ToString.class, new ToStringABC());
	assertEquals("A", x.toString(new A()));
	assertEquals("B", x.toString(new B()));
	assertEquals("C", x.toString(new C()));
	assertEquals("A", x.toString(new D()));
	assertEquals("BC", x.toString(new B(), new C()));
	assertEquals("CB", x.toString(new C(), new B()));
	assertEquals("A1", x.toString(new A(), 1));
    }

    @SuppressWarnings("serial")
    public static class TestException
	extends Exception
    {
	public TestException(String message)
	{
	    super(message);
	}
    }

    private static interface TestExceptionInterface
    {
	public void test(String message)
	    throws TestException;
    }

    private static class TestExceptionImpl
    {
	public void test(String message)
	    throws TestException
	{
	    throw new TestException(message);
	}
    }

    public void testException()
	throws Exception
    {
	TestExceptionInterface x = 
	    (TestExceptionInterface)
	    DynamicDispatch.proxy
	    (TestExceptionInterface.class, new TestExceptionImpl());

	String msg = "hello";
	boolean caught = false;
	try {
	    x.test(msg);
	}
	catch(TestException e) {
	    caught = true;
	}

	assertTrue("exception thrown & caught", caught);
    }

    public interface Add
    {
	public int add(int a, int b);
	public long add(long a, long b);
	public boolean add(boolean a, boolean b);
    }

    public void testInvoke()
	throws Exception
    {
	Object adder = new Object() {
	    @SuppressWarnings("unused")
            public int add(Object a, Object b)
	    {
	        return 0;
	    }

	    @SuppressWarnings("unused")
            public int add(int a, int b)
            {
	        return a + b;
            }

	    @SuppressWarnings("unused")
            public long add(long a, long b)
	    {
	        return a + b;
	    }
            
            @SuppressWarnings("unused")
            public boolean add(boolean a, boolean b)
            {
                return a && b;
            }
	};

	assertEquals
	    (new Integer(2), 
	     DynamicDispatch.invoke
	     (Add.class, adder, "add",
	      new Object[] { new Integer(1), new Integer(1) },
	      new Class[] { Integer.TYPE, Integer.TYPE }));

	assertEquals
	    (Boolean.TRUE,
	     DynamicDispatch.invoke
	     (Add.class, adder, "add", 
	      new Object[] { Boolean.TRUE, Boolean.TRUE },
	      new Class[] { Boolean.TYPE, Boolean.TYPE }));
    }
    
}