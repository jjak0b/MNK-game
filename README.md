# (M,N,K)-game
Generalized version of the classic tic-tac-toe, played on a matrix of size MxN.
In order to win you must align (vertically, horizontally or diagonally) K consecutive symbols.

The goal of the project is to develop a software player in able to optimally play all possible instances of (M,N,K)-game

- Command-line compile.  In the mnkgame/ directory run::

  	javac -cp ".." *.java


MNKGame application:

- Human vs Computer.  In the mnkgame/ directory run:

  	java -cp ".." mnkgame.MNKGame 3 3 3 mnkgame.RandomPlayer


- Computer vs Computer. In the mnkgame/ directory run:

  	java -cp ".." mnkgame.MNKGame 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer


MNKPlayerTester application:

- Output score only:

  java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer

- Verbose output

  java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer -v


- Verbose output and customized timeout (1 sec) and number of game repetitions (10 rounds)


	java -cp ".." mnkgame.MNKPlayerTester 5 5 4 mnkgame.RandomPlayer mnkgame.QuasiRandomPlayer -v -t 1 -r 10
