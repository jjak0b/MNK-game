# (M,N,K)-game
Generalized version of the classic tic-tac-toe, played on a matrix of size MxN.
In order to win you must align (vertically, horizontally or diagonally) K consecutive symbols.

The goal of the project is to develop a software player in able to optimally play all possible instances of (M,N,K)-game.

The player implementations of the project's objective is covered by the class `player.BestPlayer` 
and its prototype and less optimal version class `player.legacy.BestPlayerLegacy`.
Other basic level player implementations are provided in `mnkgame` package.

## Compile
- Command-line compile.  In the src/ directory run:
  ```
  javac -cp ".." *.java
  ```

## MNKGame application:

- Human vs Computer.  In the src/ directory run:
  ```
  java -cp ".." mnkgame.MNKGame 3 3 3 player.BestPlayer
  ```

- Computer vs Computer. In the src/ directory run:
  ```
  java -cp ".." mnkgame.MNKGame 5 5 4 player.BestPlayer player.legacy.BestPlayerLegacy
  ```

## MNKPlayerTester application:

- Output score only:
  ```
  java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer
  ```

- Verbose output 
  ```
  java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer -v
  ```

- Verbose output and customized timeout (1 sec) and number of game repetitions (10 rounds)
  ```
  java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer -v -t 1 -r 10
  ```

## Other credits:
- Pietro Di Lena - `mnkgame` package ( MNKGame engine, MNKPlayerTester and basic Players implementations)

