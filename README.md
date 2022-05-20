Small lib experimenting with the API that generators built on top of "Java coroutines" (aka virtual threads) could have.

Unfortunately rough performance tests suggest that the context-switching overhead is unacceptable. For a thin generator and associated consumer that sums the numbers from 0 to 1000000, the java implementation takes about 100x as long to run as the python implementation (see test.py and Main.java).

I tried a couple alternatives to the java implementation to confirm the source of slowness. Replacing the PingPong implementation with a spin-loop implementation (\_unusedSpinPingPong) results in performance comparable to the python implementation, but of course wastes a CPU. Replacing the PingPong implementation with another hacky implementation (\_unusedNosyncPingPong) that attempts to strip out most of the synchronization overhead, but leaves the context-switching overhead, does not significantly improve performance.
