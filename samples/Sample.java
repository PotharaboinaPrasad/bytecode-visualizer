public class Sample {
    private int counter;

    public Sample() {
        this.counter = 0;
    }

    public int add(int a, int b) {
        int sum = a + b;
        return sum;
    }

    public int loopSum(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }

    public boolean isEven(int x) {
        if (x % 2 == 0) {
            return true;
        } else {
            return false;
        }
    }

    public int callAdd() {
        return this.add(2, 3);
    }
}
