import java.util.Arrays;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Random;

public class PlayerSkeleton {
	 
	private final static int NUM_FEATURES = 4;
	private final static int ROWS_CLEARED = 0;
	private final static int HOLES = 1;
	private final static int BUMPINESS = 2;
	private final static int HEIGHT = 3;
	
	private static Random RANDOM = new Random();
	
	private final static int NUM_GAMES_PER_GEN = 15;
	
	private class Coord {
		public int r, c;
		public Coord(int _r, int _c) {
			r = _r;
			c = _c;
		}
	}
	
	/**
	 * Extended state class. Offers methods to
	 * test a move and compute the heuristics for
	 * the given state.
	 */
	private class StateEx extends State {
		
		//private int[][] fieldCopy;
		private int[] topCopy;
		private boolean[] fullRow;
		private LinkedList<Coord> piecePosition = new LinkedList<Coord>();
				
		private int getHoles() {
			int[][] field = getField();
			int holes = 0;

			//start looking for empty spaces below top of pile
			for (int col = 0; col < this.topCopy.length; col++){
				int startRow = this.topCopy[col];
				
				while(startRow >= 0 && fullRow[startRow])
					startRow--;
				while(startRow >= 0 && field[startRow][col] == 0)
					startRow--;
				
				for (int row = startRow; row >= 0; row--){
					//if empty space encountered below top, it's a hole
					if (field[row][col] == 0)
						holes++;
				}
			}

			return holes;
		}
		
		private int[] getBumpinessAndHeight() {			
			int bumpiness = 0;
			int aggregateHeight = topCopy[0];
			for (int i = 1; i < topCopy.length; i ++)
			{
				bumpiness += Math.abs(topCopy[i] - topCopy[i-1]);
				aggregateHeight += topCopy[i];
			}
			return new int[] {bumpiness, aggregateHeight};
		}
		
		private float testMove(int orient, int slot, float[] weights) {
			this.topCopy = Arrays.copyOf(this.getTop(), COLS);
			this.fullRow = new boolean[ROWS];
			piecePosition.clear();
			
			float score = 0;
			int piece = this.nextPiece;
			
			//initialize heuristics
			int rowsCleared = dryRunMove(piece, orient, slot);
			
			if(rowsCleared == -1) //If we lost the game, return minimal value for this move.
				return Integer.MIN_VALUE;
			
			int[] bumpinessAndHeight = getBumpinessAndHeight();
			int[] heuristics = {rowsCleared,
								getHoles(),
								bumpinessAndHeight[0],
								bumpinessAndHeight[1] };

			//score/evaluation function is dot product of heuristics[4] and weights[4]
			for (int i = 0; i < heuristics.length; i++)
				score += heuristics[i] * weights[i];
			
			//Reset the field
			int[][] field = getField();
			for(Coord c: piecePosition)
					field[c.r][c.c] = 0;
			
			return score;
			
		}
		
		/**
		 * Play the given move on our local copy of the board.
		 * @return Number of rows cleared by the move.
		 */
		private int dryRunMove(int piece, int orient, int slot) {
			int[][] field = getField();
			//height if the first column makes contact
			int height = topCopy[slot]-State.getpBottom()[piece][orient][0];
			//for each column beyond the first in the piece
			for(int c = 1; c < State.getpWidth()[piece][orient];c++) {
				height = Math.max(height,topCopy[slot+c]-State.getpBottom()[piece][orient][c]);
			}
			
			if(height+State.getpHeight()[piece][orient] >= ROWS)
				return -1;
			
			//for each column in the piece - fill in the appropriate blocks
			for(int i = 0; i < State.getpWidth()[piece][orient]; i++)
				for(int h = height+State.getpBottom()[piece][orient][i]; h < height+State.getpTop()[piece][orient][i]; h++) {
					field[h][i+slot] = -1;
					piecePosition.add(new Coord(h, i+slot));
				}
			
			//adjust top
			for(int c = 0; c < State.getpWidth()[piece][orient]; c++) {
				topCopy[slot+c]=height+State.getpTop()[piece][orient][c];
			}
			
			//check if game ended
			if(height+State.getpHeight()[piece][orient] >= ROWS)
				return 0;
			
			int rowsCleared = 0;
			
			//check for full rows - starting at the top
			for(int r = height+State.getpHeight()[piece][orient]-1; r >= height; r--) {
				//check all columns in the row
				boolean full = true;
				for(int c = 0; c < COLS; c++) {
					if(field[r][c] == 0) {
						full = false;
						break;
					}
				}
				//if the row was full - remove it and slide above stuff down
				if(full) {
					fullRow[r] = true;
					rowsCleared++;					
					//for each column
					/*for(int c = 0; c < COLS; c++) {
						//lower the top
						topCopy[c]--;
						while(topCopy[c]>=1 && field[topCopy[c]-1][c]==0)	topCopy[c]--;
					}*/
				}
				
			}
			
			return rowsCleared;
		}
		
	}

	//each indiviual is a instance of the game
	private class Individual implements Comparable<Individual> {
		//weights are in the order: Rows Cleared, Holes,bumpiness ,Height 
		public float[] features = new float[NUM_FEATURES];
		public float fitness;
		public StateEx state = new StateEx();
		
		private float EPSILON = 0.0001f;
		
		public Individual(boolean random) {
			fitness = 0;
			if(random) {
				for(int i = 0 ; i < NUM_FEATURES ; i++) {
					//In the actual project we should use this:
					if(i == ROWS_CLEARED){
						features[i] = RANDOM.nextFloat();
					}
					else{
						features[i] = (-1f)*RANDOM.nextFloat();
					}
					
					
				}
			}
		}
		
		public int compareTo(Individual a) {
			//"Natural ordering" means larger fitness first
			if(Math.abs(fitness - a.fitness) < EPSILON)
				return 0;
			else if(fitness > a.fitness)
				return -1;
			return 1;
		}
		
		public String toString() {
			return Arrays.toString(this.features);
		}
		
		public void resetState() {
			state = new StateEx();
		}
		
		public int play() {
			float maxScore;
			int bestMove;
			while(!state.hasLost()) {
				int[][] legalMoves = state.legalMoves();
				maxScore = Float.NEGATIVE_INFINITY;
				bestMove = -1;
				
				for (int i = 0; i < legalMoves.length; i++){
					float moveScore = this.state.testMove(legalMoves[i][State.ORIENT], 
														legalMoves[i][State.SLOT], 
														this.features);
					if (moveScore > maxScore){
						maxScore = moveScore;
						bestMove = i;
					}
				}
				
				state.makeMove(legalMoves[bestMove]);
			}
			
			return state.getRowsCleared();
		}
		
	}
	
	public static void main(String[] args) {
		PlayerSkeleton p = new PlayerSkeleton();
		
		if(args.length > 0 && args[0].equals("-g")) {
			p.genetic(100, 20, 0.01f);
			return;
		}
		
		/*StateEx s = p.new StateEx();
		
		new TFrame(s);
		
		while(!s.hasLost()) {
			//s.makeMove(p.pickMove(s,s.legalMoves()));
			s.draw();
			s.drawNext(0,0);
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("You have completed "+s.getRowsCleared()+" rows.");*/
	}
	
	/* FITNESS */
	private void fitness(Individual in) {
		int totalFitness = 0;
		for(int i = 0 ; i < NUM_GAMES_PER_GEN ; i++) {
			totalFitness += in.play();
			in.resetState();
		}
		
		in.fitness = totalFitness;
	}
	
	/* GENERIC GENETIC ALGORITHM STUFF DOWN HERE. */
	
	private Individual[] combine(Individual[] gen) {
		//For each pair of individuals, randomly select features from
		//either one or the other to assign to each new individual.
		Individual[] newGen = new Individual[gen.length];
		for(int i = 0 ; i < gen.length - 1 ; i += 2) {
			Individual a = new Individual(false);
			Individual b = new Individual(false);
			
			//Alternate method of breeding. Flip a coin on every feature to
			//determine which child should inherit from whom. This might be
			//better for the actual project?
			for(int j = 0 ; j < gen[i].features.length ; j++) {
				if(RANDOM.nextBoolean()) {
					a.features[j] = gen[i].features[j];
					b.features[j] = gen[i+1].features[j];
				} else {
					a.features[j] = gen[i+1].features[j];
					b.features[j] = gen[i].features[j];
				}
			}
			/*
			int split = RANDOM.nextInt(gen[i].features.length);
			for(int j = 0 ; j < split ; j++) {
				a.features[j] = gen[i].features[j];
				b.features[j] = gen[i+1].features[j];
			}
			for(int j = split ; j < gen[i].features.length ; j++) {
				a.features[j] = gen[i+1].features[j];
				b.features[j] = gen[i].features[j];
			}*/
			
			newGen[i] = a;
			newGen[i+1] = b;
		}
		return newGen;
	}
	
	private void mutate(Individual[] gen, final float mutation) {
		//Go through the features of each individual and mutate it according to the mutation rate
		for(int i = 0 ; i < gen.length ; i++) {
			for(int j = 0 ; j < gen[i].features.length ; j++) {
				if(RANDOM.nextFloat() < mutation) {
					//Again here for the purposes of the example I'm mutating by an integer value
					//Change this by a random float between 0 and 1 (maybe with a factor of .5, .25?)
					float amt = 0.f;
					while(amt == 0.f)
						amt = (RANDOM.nextBoolean() ? 1.f : -1.f) * 0.005f * RANDOM.nextFloat();
					
					gen[i].features[j] += amt;
					if(gen[i].features[j] > 1.f)
						gen[i].features[j] = 1.f;
					else if(gen[i].features[j] < -1.f)
						gen[i].features[j] = -1.f;
				}
			}
		}
	}
	
	private void genetic(final int gen_size, final int num_gens, final float mutation) {
		Individual[] current_gen = new Individual[gen_size];
		Individual[] better_half = new Individual[gen_size/2];
		Individual best = null;
		PriorityQueue<Individual> leaderboard = new PriorityQueue<Individual>(gen_size);
		int k = 0;
		//Create first generation
		for(int i = 0 ; i < gen_size ; i++)
			current_gen[i] = new Individual(true);
		
		//Here we could just run forever, or ensure that our fitness function
		//never returns 1. Or stop after some amount of iterations.
		while(k < num_gens) {
			for(int i = 0 ; i < current_gen.length ; i++) {
				fitness(current_gen[i]);
				leaderboard.add(current_gen[i]);
			}
			
			best = leaderboard.peek();
			
			for(int i = 0 ; i < current_gen.length / 2 ; i++)
				better_half[i] = leaderboard.remove();
			leaderboard.clear();
			
			current_gen = combine(better_half);
			mutate(current_gen, mutation);
			
			System.out.println("Generation " + k + ", best individual: " 
					+ best.toString() + " (fitness " + best.fitness + ")");
			k++;
		};
	}
	
	
}
