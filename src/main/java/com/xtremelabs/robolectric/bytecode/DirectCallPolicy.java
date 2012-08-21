package com.xtremelabs.robolectric.bytecode;

/**
 * Policy that defined how direct calls are handled. Policy is thread local.
 */
public interface DirectCallPolicy {

    /** NoDirectCallPolicy instance. */
    DirectCallPolicy NOP = new NoDirectCallPolicy();

    /**
     * Decide whether call must be performed directly. 
     * @param target target object
     * @return true if call must be direct
     */
    boolean shouldCallDirectly(Object target);

    /**
     * Called after each object method invocation. 
     * It's executed in <code>finally</code> block.
     * @param target object which method has been invoked on
     * @return policy to replace current instance
     */
    DirectCallPolicy onMethodInvocationFinished(Object target);

    /**
     * Called when direct call request appears. This method can either throw {@link DirectCallException} or return false 
     * if policy change is not appropriate. 
     * @param previousPolicy previous policy instance
     * @return true if policy change should be applied
     */
    boolean checkForChange(DirectCallPolicy previousPolicy);

    /** Throw by policy instances in case of illegal state exception. */
    public static class DirectCallException extends IllegalStateException {

        private static final long serialVersionUID = 4326926182637261742L;

        public DirectCallException(String msg) {
            super(msg);
        }

    }

    /** Direct call is not performed. */
    public static class NoDirectCallPolicy implements DirectCallPolicy  {

        private NoDirectCallPolicy() { /* hidden */ }

        @Override
        public boolean shouldCallDirectly(Object target) {
            return false;
        }
        @Override
        public DirectCallPolicy onMethodInvocationFinished(Object target) {
            return this;
        }
        @Override
        public boolean checkForChange(DirectCallPolicy previousPolicy) {
            return true;
        }
    }

    /** Direct call is performed once only. */
    public static class OneShotDirectCallPolicy implements DirectCallPolicy {

        /** Direct instance. */
        private Object expectedInstance;

        public OneShotDirectCallPolicy(Object directInstance) {
            this.expectedInstance = directInstance;
        }

        @Override
        public boolean shouldCallDirectly(Object target) {
            if (expectedInstance == null) { return false; }
            
            if (expectedInstance != target) {
                Object expected = expectedInstance;
                expectedInstance = null;
                throw new DirectCallException("expected to perform direct call on <" + expected + "> but got <" + target + ">");
            }
            
            expectedInstance = null;
            return true;
        }

        @Override
        public DirectCallPolicy onMethodInvocationFinished(Object target) {
            return NOP;
        }
        
        @Override
        public boolean checkForChange(DirectCallPolicy previousPolicy) {
            // first setup
            if (previousPolicy == NOP) { return true; }
            
            // twice setup
            if (previousPolicy instanceof OneShotDirectCallPolicy) {
                throw new DirectCallException("already expecting a direct call on <" + ((OneShotDirectCallPolicy) previousPolicy).expectedInstance + "> but here's a new request for <" + expectedInstance + ">");
            }
            
            // we are inside full stack direct call => do not change anything
            if (previousPolicy instanceof FullStackDirectCallPolicy) {
                return false;
            }
            
            // unexpected
            throw new DirectCallException("Direct call policy is already set to " + previousPolicy);
        }
    }

    /** Direct call is performed within the invocation full stack. */
    public static class FullStackDirectCallPolicy implements DirectCallPolicy {

        /** Stack depth. */
        private int depth = -1;

        @Override
        public boolean shouldCallDirectly(Object target) {
            ++depth;
            return true;
        }

        @Override
        public DirectCallPolicy onMethodInvocationFinished(Object target) {
            if (depth < 0) {
                throw new DirectCallException("Stack depth is negative: " + depth);
            }
            return depth-- == 0 ? NOP : this;
        }

        @Override
        public boolean checkForChange(DirectCallPolicy previousPolicy) {
            // first setup
            if (previousPolicy == NOP) { return true; }
            
            // we are inside full stack direct call => do not change anything
            if (previousPolicy instanceof FullStackDirectCallPolicy) {
                return false;
            }
            
            // unexpected, bad setup
            throw new DirectCallException("Direct call policy is already set to " + previousPolicy);
        }
        
    }

}
