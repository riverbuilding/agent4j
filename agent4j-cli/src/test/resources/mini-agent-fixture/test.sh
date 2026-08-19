#!/bin/sh
set -eu
javac Calculator.java CalculatorTest.java
java CalculatorTest
