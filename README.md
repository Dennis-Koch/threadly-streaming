# threadly-streaming

## Key features of this library
### 1) [Chain-Revert API](#chain-revert-api)
### 2) [Bullet-proof Java Parallel-Streaming API](#parallel-streaming-api)

---
A very simple example might look like this:

#  <a name="chain-revert-api"></a>Chain-Revert API
This library is intended to help in usecases where you have a state transition in your system that you want to make "
revertable". So it is about applying the [Stack pattern](https://en.wikipedia.org/wiki/Stack_(abstract_data_type)) to
your state transition in a reentrant manner. So you can chain revertable state transitions of any complexity globally or
thread-local to your current thread.

### try 1: Without threadly-streaming

```java
setFooState(fooValue1);
run_logic_that_works_with_state_of_foo();
clearFooState();
```

**This common approach has a major robustness flaw:**

If an error occurs during the execution of `run_logic_that_works_with_state_of_foo()` then `clearFooState()` is not
invoked and we may have a memory leak or might even expose sensitive customer data to other requests served by the same
thread in successor tasks

So lets try a better version - still without dedicated library support...

### try 2: Without threadly-streaming, but without flaw #1

```java
setFooState(fooValue1);
try {
  run_logic_that_works_with_state_of_foo();
} finally {
  clearFooState();
}
```

**This approach still has a major robustness issues:** It is not reentrant - This means that multiple executions of the
same method body by the same thread may corrupt the underlying thread-local state!

Imagine the following sequence

```
setFooState(fooValue1)                         // here fooValue1 is assigned to foo state
run_logic_that_works_with_state_of_foo()       // fooValue1 is used in the logic
   > setFooState(fooValue2)                    // a cascaded algorithm within "runLogic" leads to some logic that prepares foo state (again)
   > run_logic_that_works_with_state_of_foo()  // fooValue2 is used in the logic
   > clearFooState()                           // no we clear fooValue2 from the foo state. it is now null, but not fooValue1 like before
...  !!                                        // at this moment - we are still somewhere within the initial call to run_logic - we have lost our fooValue1 state!
clearFooState()                                // no we think we clear fooValue1 from the foo state. but it was already null
```

But of course we can fix this, right? We have solved problems of this kind already a lot and we are all experts in
problem solving. To make it more interesting we now introduce also a 2nd state that we want to make revertable in
addition to our foo state. And we make it so that the fooState is applied conditionally...

### try 3: Without threadly-streaming, but also without flaw #1 & #2

```java
var oldFooState = getFooState();
var oldBarState = getBarState();
if (conditionIsSatisfied) {
    setFooState(fooValue1);
}
setBarState(barValue1);
try {
  run_logic_that_works_with_state_of_foo_and_bar();
} finally {
  setBarState(oldBarState);
  if (conditionIsSatisfied){
    setFooState(oldFooState);
  }
}
```

**You might already expect it: This approach - despite all the increased clutter that we had to produce already - still
hides some severe robustness flaws from us:**

What would happen if any exception occurs during the execution of `setBarState()`? In such a case our finally logic
would not apply and we have an uncaught exception leaving a dirty fooState to our thread. So we are again left alone
with flaw No.1. Of course we could adhoc fix this by moving the `setBarState()` invocation into the try section to solve
our "transactional" problem. An ugly - but admitted truly robust - solution would then look like this: 

### try 3: Without threadly-streaming, but also without flaw #1, #2 & #3

```java
var oldFooState = getFooState();
if (conditionIsSatisfied) {
  setFooState(fooValue1);
}
try {
  var oldBarState = getBarState();
  setBarState(barValue1);
  try {
    run_logic_that_works_with_state_of_foo_and_bar();      
  } finally {
    setBarState(oldBarState);
  }
} finally {
  if (conditionIsSatisfied){
    setFooState(oldFooState);
  }  
}
```

In real case scenarios it sometimes because even more complex than this. Normally the solution is either to drop the
requirement of bullet-proof code altogether and simply accept the "negligible" risk that unexpected things might happen.
Or you live with the fact that your whole codebase is cluttered with nested try/finally statements all over the place.

**Or you use _threadly-streaming_ that gives you all the robustness from the code before - without the clutter**:

```java
var revert = DefaultStateRevert.chain(chain -> {
    if (conditionIsSatisfied) {
        chain.append(pushFooState(fooValue1));
    }
    chain.append(pushBarState(barValue1));
});
try{
  run_logic_that_works_with_state_of_foo_and_bar();
} finally {
  revert.revert();    
}
```

Note that we also changed the semantics of the encapsulated state transitions. Our helper methods are not called
`pushFooState()` instead of `setFooState()` etc.

Note the code example above is 100% transactionally consistent. If an error or exception of any kind happens during the
execution of `pushFooState()`, `pushBarState()` or the normal business logic in
`run_logic_that_works_with_state_of_foo_and_bar()` the overall termination and cleanup logic will recover to the state
of foo & bar from the very initial state. Even the partial creation of the lamda structure within the `chain()` method
is properly reverted behind the scenes.

It is also reentrant-capable due to the stack nature with _push*_ instead of _set*_ and due to the fact that we store
the revert-closure (the result from the `chain()` invocation) on the thread stack it is immutable & thread-safe as well.

This logic is even more robust than even the best common example from above because it will only memorize & apply the
revert logic for the `fooState` if the `conditionIsSatisfied` was condition was really applicable at a single point in
time. Note the subtle difference above: In our nested try/finally we call `conditionIsSatisfied` 2 times and even if it
is false we have allocated the `oldFooState` on our stack unnecessarily. If the fooState creation itself was a
non-trivial task we have initialized a complex object on our stack without any good reason. This is also solved with the
_Chain-API_ from _threadly-streaming_.

---
#  <a name="parallel-streaming-api"></a>Parallel Streaming API

TODO