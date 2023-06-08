import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;

public class MoveGenerator{

	public boolean white;
	public boolean filterPseudos;
	public boolean generateCastles;
	public boolean generateEnPassant;
	public long empty = 0L;
	public long occupied = 0L;
	public long whitePieces = 0L;
	public long blackPieces = 0L;
	public long clearLeftBit = 9223372036854775807L;
	public ArrayList<int[]> moves = new ArrayList<int[]>();
	public ArrayList<Integer> pawnTargets = new ArrayList<Integer>();
	
	public MoveGenerator(boolean white, boolean filterPseudos, boolean generateCastles, boolean generateEnPassant){
	
		this.white = white;
		this.filterPseudos = filterPseudos;
		this.generateCastles = generateCastles;
		this.generateEnPassant = generateEnPassant;
		updateMasks();
		
	}
	
	public void updateMasks(){
		
		occupied = BitData.WP|BitData.BP|BitData.WN|BitData.BN|BitData.WB|BitData.BB|BitData.WR|BitData.BR|BitData.WQ|BitData.BQ|BitData.WK|BitData.BK;
		whitePieces = BitData.WP|BitData.WN|BitData.WB|BitData.WR|BitData.WQ|BitData.WK;
		empty = ~occupied;
		blackPieces = occupied&~whitePieces;
	
	}
	
	public void generateMoves(){
	
		if(white){
			generateWhitePawn();
			generateWhiteKnight();
			generateWhiteBishop();
			generateWhiteRook();
			generateWhiteQueen();
			generateWhiteKing();
			if(generateCastles)
				generateCastles();
			if(generateEnPassant)
				generateWhiteEnPassant();
			if(filterPseudos)
				filterPseudos();
		}
		else{
			generateBlackPawn();
			generateBlackKnight();
			generateBlackBishop();
			generateBlackRook();
			generateBlackQueen();
			generateBlackKing();
			if(generateCastles)
				generateCastles();
			if(generateEnPassant)
				generateBlackEnPassant();
			if(filterPseudos)
				filterPseudos();
		}
	
	}
	
	public void speedTest(){
	
		GenerateMoves g = new GenerateMoves(true, false, false, false);
		System.out.println("methodA:");
		long start = System.currentTimeMillis();
		for(long i = 0L; i < 10000000L; i++){
			g.generateKingMoves();
		}
		System.out.println(System.currentTimeMillis() - start + "\nMethodb:");
		start = System.currentTimeMillis();
		for(long i = 0L; i < 10000000L; i++){
			generateWhiteKing();
		}
		System.out.println(System.currentTimeMillis()-start);
	
	}
	
	public void sortMoves(){

		HashMap<int[], Double> moveValues = new HashMap<>();
		for (int[] move : moves)
			moveValues.put(move, computeValue(move[1]));
		moves.sort(Comparator.comparingDouble(moveValues::get).reversed());
		
	}
	    
	public double computeValue(int move){
		
		if(move < 0)
			return 0;
		double score = 0;
		int moveShift = 63-move;
		generatePawnAttackSquares(!white);
		if(pawnTargets.contains(move))
			score -= 4;
		return score + BitData.indexToAttackValue(moveShift);
		
	}
	
	public void generatePawnAttackSquares(boolean white){
	
		if(white){
			long moveMap = (BitData.WP<<7)&~BitData.fileMasks[0]; //north east
			int leadingZeros = Long.numberOfLeadingZeros(moveMap);
			while(leadingZeros != 64){
				pawnTargets.add(leadingZeros);
				moveMap <<= leadingZeros+1;
				moveMap >>>= leadingZeros+1;
				leadingZeros = Long.numberOfLeadingZeros(moveMap);
			}
			moveMap = (BitData.WP<<9)&~BitData.fileMasks[7]; //north west
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
			while(leadingZeros != 64){
				pawnTargets.add(leadingZeros);
				moveMap <<= leadingZeros+1;
				moveMap >>>= leadingZeros+1;
				leadingZeros = Long.numberOfLeadingZeros(moveMap);
			}
		}else{
			long moveMap = (BitData.BP>>>7)&~BitData.fileMasks[7]; //south west
			int leadingZeros = Long.numberOfLeadingZeros(moveMap);
			while(leadingZeros != 64){
				pawnTargets.add(leadingZeros);
				moveMap <<= leadingZeros+1;
				moveMap >>>= leadingZeros+1;
				leadingZeros = Long.numberOfLeadingZeros(moveMap);
			}
			moveMap = (BitData.BP>>>9)&~BitData.fileMasks[0]; //south east
			int trailingZeros = Long.numberOfTrailingZeros(moveMap);
			while(trailingZeros != 64){
				pawnTargets.add(63-trailingZeros);
				moveMap >>>= trailingZeros+1;
				moveMap <<= trailingZeros+1;
				trailingZeros = Long.numberOfTrailingZeros(moveMap);
			}
		}
	
	}
	
	public void filterPseudos(){
	
		int piece = 0;
		int move = 0;
		int type = 0;
		char capture = '-';
		boolean promotion = false;
	
		for(int i = 0; i < moves.size(); i++){
			piece = moves.get(i)[0];
			move = moves.get(i)[1];
			type = moves.get(i)[2];
			if(white){
				capture = BitData.indexToBlackSymbol(63-move);
				promotion = (type == 80 && (move>>>3) == 0);
				BitData.pseudoUpdateWhite(piece, move, type, promotion);
			}
			else{
				capture = BitData.indexToWhiteSymbol(63-move);
				promotion = (type == 112 && (move>>>3) == 7);
				BitData.pseudoUpdateBlack(piece, move, type, promotion);
			}
			
			if(inCheck()){
				moves.remove(i);
				i--;
			}
			if(white)
				BitData.undoUpdateWhite(piece, move, type, capture, promotion);
			else
				BitData.undoUpdateBlack(piece, move, type, capture, promotion);
		}
	
	}
	
	public void generateWhiteEnPassant(){
	
		long passers = BitData.WP&BitData.rankMasks[3];
		long passables = BitData.BP&BitData.rankMasks[3];
		if(passers == 0 || passables == 0)
			return;
		int passerIndex = Long.numberOfLeadingZeros(passers);
		int passableIndex = Long.numberOfLeadingZeros(passables);
		long passableCopy = passables;
		while(passerIndex != 64){
			while(passableIndex != 64){
				int difference = passerIndex-passableIndex;
				if(Math.abs(difference) == 1 && ChessManager.pawnPushes[passableIndex-16]){
					moves.add(new int[] {passerIndex, passerIndex-8-difference, 69});
				}
				passableCopy <<= passableIndex;
				passableCopy &= clearLeftBit;
				passableCopy >>>= passableIndex;
				passableIndex = Long.numberOfLeadingZeros(passableCopy);
			}
			passableCopy = passables;
			passableIndex = Long.numberOfLeadingZeros(passables);
			passers <<= passerIndex;
			passers &= clearLeftBit;
			passers >>>= passerIndex;
			passerIndex = Long.numberOfLeadingZeros(passers);
		}
		
	}
	
	public void removeRepetitions(){
	
		int repetitionCount = 0;
		if(white){
			for(int i = 0; i < moves.size(); i++){
				for(int j = 0; j < ChessManager.whiteMoveCache.length; j++){
					if(Evaluation.areEquivalent(new int[] {moves.get(i)[0], moves.get(i)[1]},ChessManager.whiteMoveCache[j])){
						if(Evaluation.areEquivalent(ChessManager.board, ChessManager.positionCache[j*2])){
							repetitionCount++;
						}
					}
					if(repetitionCount >= 1){
						moves.remove(i);
						i--;
						repetitionCount = 0;
						break;
					}
				}
				repetitionCount = 0;
			}
		}
		else{
			for(int i = 0; i < moves.size(); i++){
				for(int j = 0; j < ChessManager.blackMoveCache.length; j++){
					if(Evaluation.areEquivalent(new int[] {moves.get(i)[0], moves.get(i)[1]},ChessManager.blackMoveCache[j])){
						if(Evaluation.areEquivalent(ChessManager.board, ChessManager.positionCache[j*2])){
							repetitionCount++;
						}
					}
					if(repetitionCount >= 1){
						moves.remove(i);
						i--;
						repetitionCount = 0;
						break;
					}
				}
				repetitionCount = 0;
			}
		}
	
	}
	
	public void generateBlackEnPassant(){
	
		long passers = BitData.BP&BitData.rankMasks[4];
		long passables = BitData.WP&BitData.rankMasks[4];
		if(passers == 0 || passables == 0)
			return;
		int passerIndex = Long.numberOfLeadingZeros(passers);
		int passableIndex = Long.numberOfLeadingZeros(passables);
		long passableCopy = passables;
		while(passerIndex != 64){
			while(passableIndex != 64){
				int difference = passerIndex-passableIndex;
				if(Math.abs(difference) == 1 && ChessManager.pawnPushes[passableIndex-32]){
					moves.add(new int[] {passerIndex, passerIndex+8-difference, 101});
				}
				passableCopy <<= passableIndex;
				passableCopy &= clearLeftBit;
				passableCopy >>>= passableIndex;
				passableIndex = Long.numberOfLeadingZeros(passableCopy);
			}
			passableCopy = passables;
			passableIndex = Long.numberOfLeadingZeros(passables);
			passers <<= passerIndex;
			passers &= clearLeftBit;
			passers >>>= passerIndex;
			passerIndex = Long.numberOfLeadingZeros(passers);
		}
		
	}
	
	public void generateCastles(){
	
		if(inCheck())
			return;
		if(white){
			if(ChessManager.whiteShortRights && ((occupied&6) == 0) && !isAttacked(61))
				moves.add(new int[] {60, -1, 75});
			if(ChessManager.whiteLongRights && ((occupied&112) == 0) && !isAttacked(59))
				moves.add(new int[] {60, -2, 75});
		}
		else{
			if(ChessManager.blackShortRights && ((occupied&432345564227567616L) == 0) && !isAttacked(5))
				moves.add(new int[] {4, -1, 107});
			if(ChessManager.blackLongRights && ((occupied&8070450532247928832L) == 0) && !isAttacked(3))
				moves.add(new int[] {4, -2, 107});
		}
	
	}
	
	public boolean inCheck(){
	
		int kingSquare = white? Long.numberOfLeadingZeros(BitData.WK): Long.numberOfLeadingZeros(BitData.BK);
		MoveGenerator g = new MoveGenerator(!white, false, false, false);
		g.generateMoves();
		for(int i = 0; i < g.moves.size(); i++){
			if(g.moves.get(i)[1] == kingSquare)
				return true;
		}
		return false;
	
	}
	
	public boolean isAttacked(int square){
	
		MoveGenerator g = new MoveGenerator(!white, false, false, false);
		g.generateMoves();
		for(int i = 0; i < g.moves.size(); i++){
			if(g.moves.get(i)[1] == square)
				return true;
		}
		return false;
	
	}
	
	public void generateWhiteKing(){
	
		int kingIndex = Long.numberOfLeadingZeros(BitData.WK);
		long moveMap = BitData.kingMasks[kingIndex]&~whitePieces;
		int moveIndex = Long.numberOfLeadingZeros(moveMap);
		while(moveIndex != 64){
			moves.add(new int[] {kingIndex, moveIndex, 75});
			moveMap <<= moveIndex;
			moveMap &= clearLeftBit;
			moveMap >>>= moveIndex;
			moveIndex = Long.numberOfLeadingZeros(moveMap);
		}
	
	}
	
	public void generateBlackKing(){
	
		int kingIndex = Long.numberOfLeadingZeros(BitData.BK);
		long moveMap = BitData.kingMasks[kingIndex]&~blackPieces;
		int moveIndex = Long.numberOfLeadingZeros(moveMap);
		while(moveIndex != 64){
			moves.add(new int[] {kingIndex, moveIndex, 107});
			moveMap <<= moveIndex;
			moveMap &= clearLeftBit;
			moveMap >>>= moveIndex;
			moveIndex = Long.numberOfLeadingZeros(moveMap);
		}
	
	}
	
	public void generateWhiteKnight(){
	
		long WNCopy = BitData.WN;
		int knightIndex = Long.numberOfLeadingZeros(WNCopy);
		while(knightIndex != 64){
			long moveMap = BitData.knightMasks[knightIndex]&~whitePieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {knightIndex, moveIndex, 78});
				moveMap <<= moveIndex;
				moveMap &= clearLeftBit;
				moveMap >>>= moveIndex;
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			WNCopy <<= knightIndex;
			WNCopy &= clearLeftBit;
			WNCopy >>>= knightIndex;
			knightIndex = Long.numberOfLeadingZeros(WNCopy);
		}
	
	}
	
	public void generateBlackKnight(){
	
		long BNCopy = BitData.BN;
		int knightIndex = Long.numberOfLeadingZeros(BNCopy);
		while(knightIndex != 64){
			long moveMap = BitData.knightMasks[knightIndex]&~blackPieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {knightIndex, moveIndex, 110});
				moveMap <<= moveIndex;
				moveMap &= clearLeftBit;
				moveMap >>>= moveIndex;
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			BNCopy <<= knightIndex;
			BNCopy &= clearLeftBit;
			BNCopy >>>= knightIndex;
			knightIndex = Long.numberOfLeadingZeros(BNCopy);
		}
	
	}
	
	public void generateWhiteQueen(){
	
		long WQCopy = BitData.WQ;
		int queenIndex = Long.numberOfLeadingZeros(WQCopy);
		while(queenIndex != 64){
			long moveMap = (generateOrthogonal(queenIndex)|generateDiagonal(queenIndex))&~whitePieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {queenIndex, moveIndex, 81});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			WQCopy <<= queenIndex;
			WQCopy &= clearLeftBit;
			WQCopy >>>= queenIndex;
			queenIndex = Long.numberOfLeadingZeros(WQCopy);
		}
	
	}
	
	public void generateBlackQueen(){
	
		long BQCopy = BitData.BQ;
		int queenIndex = Long.numberOfLeadingZeros(BQCopy);
		while(queenIndex != 64){
			long moveMap = (generateOrthogonal(queenIndex)|generateDiagonal(queenIndex))&~blackPieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {queenIndex, moveIndex, 113});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			BQCopy <<= queenIndex;
			BQCopy &= clearLeftBit;
			BQCopy >>>= queenIndex;
			queenIndex = Long.numberOfLeadingZeros(BQCopy);
		}
	
	}
	
	public void generateWhiteBishop(){
	
		long WBCopy = BitData.WB;
		int bishopIndex = Long.numberOfLeadingZeros(WBCopy);
		while(bishopIndex != 64){
			long moveMap = generateDiagonal(bishopIndex)&~whitePieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {bishopIndex, moveIndex, 66});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			WBCopy <<= bishopIndex;
			WBCopy &= clearLeftBit;
			WBCopy >>>= bishopIndex;
			bishopIndex = Long.numberOfLeadingZeros(WBCopy);
		}
	
	}
	
	public void generateBlackBishop(){
	
		long BBCopy = BitData.BB;
		int bishopIndex = Long.numberOfLeadingZeros(BBCopy);
		while(bishopIndex != 64){
			long moveMap = generateDiagonal(bishopIndex)&~blackPieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {bishopIndex, moveIndex, 98});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			BBCopy <<= bishopIndex;
			BBCopy &= clearLeftBit;
			BBCopy >>>= bishopIndex;
			bishopIndex = Long.numberOfLeadingZeros(BBCopy);
		}
	
	}
	
	public void generateWhiteRook(){
		
		long WRCopy = BitData.WR;
		int rookIndex = Long.numberOfLeadingZeros(WRCopy);
		while(rookIndex != 64){
			long moveMap = generateOrthogonal(rookIndex)&~whitePieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {rookIndex, moveIndex, 82});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			WRCopy <<= rookIndex;
			WRCopy &= clearLeftBit;
			WRCopy >>>= rookIndex;
			rookIndex = Long.numberOfLeadingZeros(WRCopy);
		}
	
	}
	
	public void generateBlackRook(){
		
		long BRCopy = BitData.BR;
		int rookIndex = Long.numberOfLeadingZeros(BRCopy);
		while(rookIndex != 64){
			long moveMap = generateOrthogonal(rookIndex)&~blackPieces;
			int moveIndex = Long.numberOfLeadingZeros(moveMap);
			while(moveIndex != 64){
				moves.add(new int[] {rookIndex, moveIndex, 114});
				moveMap <<= (moveIndex);
				moveMap &= clearLeftBit;
				moveMap >>>= (moveIndex);
				moveIndex = Long.numberOfLeadingZeros(moveMap);
			}
			BRCopy <<= rookIndex;
			BRCopy &= clearLeftBit;
			BRCopy >>>= rookIndex;
			rookIndex = Long.numberOfLeadingZeros(BRCopy);
		}
	
	}
	
	public long generateOrthogonal(int pieceIndex){
		
		long pieceMap = 1L<<(63-pieceIndex);
		long horizontalMap = (occupied-(pieceMap<<1))^Long.reverse(Long.reverse(occupied)-(Long.reverse(pieceMap)<<1));
		long verticalMap = ((occupied&BitData.fileMasks[pieceIndex&7])-(pieceMap<<1))^Long.reverse(Long.reverse(occupied&BitData.fileMasks[pieceIndex&7])-(Long.reverse(pieceMap)<<1));
		return (horizontalMap&BitData.rankMasks[(pieceIndex>>>3)])|(verticalMap&BitData.fileMasks[pieceIndex&7]);
		
	}
	
	public long generateDiagonal(int pieceIndex){
	
		long pieceMap = 1L<<(63-pieceIndex);
		long diagonalMap = ((occupied&BitData.diagonalMasks[(pieceIndex>>>3)+(pieceIndex&7)])-(pieceMap<<1))^Long.reverse(Long.reverse(occupied&BitData.diagonalMasks[(pieceIndex>>>3)+(pieceIndex&7)])-(Long.reverse(pieceMap)<<1));
		long antiDiagonalMap = ((occupied&BitData.antiDiagonalMasks[(pieceIndex>>>3)+7-(pieceIndex&7)])-(pieceMap<<1))^Long.reverse(Long.reverse(occupied&BitData.antiDiagonalMasks[(pieceIndex>>>3)+7-(pieceIndex&7)])-(Long.reverse(pieceMap)<<1));
		return (diagonalMap&BitData.diagonalMasks[(pieceIndex>>>3)+(pieceIndex&7)])|(antiDiagonalMap&BitData.antiDiagonalMasks[(pieceIndex>>>3)+7-(pieceIndex&7)]);
	
	}
	
	public void generateWhitePawn(){
	
		long moveMap = (BitData.WP<<7)&blackPieces&~BitData.fileMasks[0]; //north east
		int leadingZeros = Long.numberOfLeadingZeros(moveMap);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros+7, leadingZeros, 80});
			moveMap <<= leadingZeros+1;
			moveMap >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
		}
		moveMap = (BitData.WP<<9)&blackPieces&~BitData.fileMasks[7]; //north west
		leadingZeros = Long.numberOfLeadingZeros(moveMap);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros+9, leadingZeros, 80});
			moveMap <<= leadingZeros+1;
			moveMap >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
		}
		moveMap = (BitData.WP<<8)&empty; //single push
		long temporary = moveMap;
		leadingZeros = Long.numberOfLeadingZeros(temporary);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros+8, leadingZeros, 80});
			temporary <<= leadingZeros+1;
			temporary >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(temporary);
		}
		moveMap = (moveMap<<8)&empty&BitData.rankMasks[4]; //double push
		leadingZeros = Long.numberOfLeadingZeros(moveMap);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros+16, leadingZeros, 80});
			moveMap <<= leadingZeros+1;
			moveMap >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
		}
	
	}
	
	public void generateBlackPawn(){
		
		long moveMap = (BitData.BP>>>7)&whitePieces&~BitData.fileMasks[7]; //south west
		int leadingZeros = Long.numberOfLeadingZeros(moveMap);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros-7, leadingZeros, 112});
			moveMap <<= leadingZeros+1;
			moveMap >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
		}
		moveMap = (BitData.BP>>>9)&whitePieces&~BitData.fileMasks[0]; //south east
		int trailingZeros = Long.numberOfTrailingZeros(moveMap);
		while(trailingZeros != 64){
			moves.add(new int[] {63-trailingZeros-9, 63-trailingZeros, 112});
			moveMap >>>= trailingZeros+1;
			moveMap <<= trailingZeros+1;
			trailingZeros = Long.numberOfTrailingZeros(moveMap);
		}
		moveMap = (BitData.BP>>>8)&empty; //single push
		long temporary = moveMap;
		trailingZeros = Long.numberOfTrailingZeros(temporary);
		while(trailingZeros != 64){
			moves.add(new int[] {63-trailingZeros-8, 63-trailingZeros, 112});
			temporary >>>= trailingZeros+1;
			temporary <<= trailingZeros+1;
			trailingZeros = Long.numberOfTrailingZeros(temporary);
		}
		moveMap = (moveMap>>>8)&empty&BitData.rankMasks[3]; //double push
		leadingZeros = Long.numberOfLeadingZeros(moveMap);
		while(leadingZeros != 64){
			moves.add(new int[] {leadingZeros-16, leadingZeros, 112});
			moveMap <<= leadingZeros+1;
			moveMap >>>= leadingZeros+1;
			leadingZeros = Long.numberOfLeadingZeros(moveMap);
		}
	
	}
	
	public void printMoves(){
	
		for(int i = 0; i < moves.size(); i++){
			if(moves.get(i)[1] < 0){
				String move = moves.get(i)[1] == -1? "scastle": "lcastle";
				System.out.printf("{%s, %s}\n", ChessManager.cboard[moves.get(i)[0]], move);
				continue;
			}
			System.out.printf("{%s, %s}\n", ChessManager.cboard[moves.get(i)[0]], ChessManager.cboard[moves.get(i)[1]]);
		}
		System.out.println("\n");
	
	}
	
}
