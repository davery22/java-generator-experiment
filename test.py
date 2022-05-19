from timeit import default_timer as timer

def gen():
    i = 0
    while i < 1000000:
        yield i
        i += 1

def measure():
    start = timer()
    sum = 0
    for i in gen():
        sum += i
    end = timer()
    print("Sum: %d, Elapsed: %s" % (sum, end-start))

if __name__ == "__main__":
    measure()
