# threadly-streaming

## Key features of this library

### 1) [Chain-Revert API](#chain-revert-api)

### 2) [Bullet-proof Java Parallel-Streaming API](#parallel-streaming-api)

---
A very simple example might look like this:

# <a name="chain-revert-api"></a>Chain-Revert API

This small but powerful API is intended to help in usecases where you have a state transition in your system that you
want to make _revertable_. Revertable means here that we want to restore the state before said transition, similar to a
transaction rollback (but much more lightweight). In other words we intend to apply the
[stack pattern](https://en.wikipedia.org/wiki/Stack_(abstract_data_type)) to your state transition in a thread-local and
re-entrant manner. As a result of such a capability you can easily chain also multiple revertable state transitions into
one easy to handle _revertable_. While doing these we still ensure that internal implementation details of a component
that describe how to revert a state is not disclosed to the caller.

### try 1: example without threadly-streaming

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

### try 2: example without threadly-streaming, but having solved flaw #1

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
clearFooState()                                // now we think we clear fooValue1 from the foo state. but it was already null
```

But of course we can fix this, right? We have solved problems of this kind already a lot and we are all experts in
problem solving. To make it more interesting we now introduce also a 2nd state that we want to make revertable in
addition to our foo state. And we make it so that the fooState is applied conditionally...

### try 3: example without threadly-streaming, but also having solved flaw #1 & #2

```java
var oldFooState = getFooState();
var oldBarState = getBarState();
if (conditionIsSatisfied) {
  setFooState(fooValue1);
}
setBarState(barValue1);
try  {
  run_logic_that_works_with_state_of_foo_and_bar();
} finally {
  setBarState(oldBarState);
  if (conditionIsSatisfied) {
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

### try 3: example without threadly-streaming and having solved all findings

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
  if(conditionIsSatisfied) {
    setFooState(oldFooState);
  }
}
```

In real case scenarios it often becomes even more complex than what we have seen here in our example above. Normally the
solution is either to
drop the requirement of bullet-proof code altogether and simply accept the "negligible" risk that unexpected things
might happen. Or you live with the fact that your whole codebase is cluttered with nested try/finally statements all
over the place.

**Or you use _threadly-streaming_ that gives you all the robustness from the code before - without the clutter**:

```java
var revert = DefaultStateRevert.chain(chain -> {
    if (conditionIsSatisfied) {
        chain.append(pushFooState(fooValue1));
    }
    chain.append(pushBarState(barValue1));
});
try {
  run_logic_that_works_with_state_of_foo_and_bar();
} finally {
  revert.revert();    
}
```

- We also changed the semantics of the encapsulated state transitions. Our helper methods are not called
  `setFooState()` anymore but rather `pushFooState()`. Same for the barState.

- The code example above is 100% transactionally consistent. If an error or exception of any kind happens during the
  execution of `pushFooState()`, `pushBarState()` or the normal business logic in
  `run_logic_that_works_with_state_of_foo_and_bar()` the overall termination and cleanup logic will recover to the state
  of foo & bar from the very initial state. Even the partial creation of the lamda structure within the `chain()` method
  is properly reverted behind the scenes.

- It is also reentrant-capable due to the stack nature with _push*_ instead of _set*_ and due to the fact that we store
  the revert-closure (the result from the `chain()` invocation) on the thread stack it is immutable & thread-safe as
  well.

- In fact the managed logic is even more robust than even the best common example from above because it will only
  memorize & apply the
  revert logic for the `fooState` if the `conditionIsSatisfied` condition was really applicable at a single point in
  time

- Note the subtle difference in our nested try/finally above we call `conditionIsSatisfied` 2 times and even if it
  resolves to _false_ we have allocated the `oldFooState` on our stack unnecessarily. If the fooState creation itself
  was a
  non-trivial or lazy task we have initialized a complex object on our stack without any good reason. But this is also
  solved with the _Chain-API_ from _threadly-streaming_.

Try it out!

---

# <a name="parallel-streaming-api"></a>Parallel Streaming API

TODO
