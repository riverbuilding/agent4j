public final class CalculatorTest {
    public static void main(String[] args) {
        if (Calculator.add(2, 3) != 5) {
            throw new AssertionError("add should sum its arguments");
        }
    }
}
