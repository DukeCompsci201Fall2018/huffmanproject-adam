import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	private String[] encodings = new String[ALPH_SIZE + 1];

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);

		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();


	}

	/**
	 * Counts the frequency of each 8 bit word
	 * @param in BitInputStream
	 * @return int[] with counts of each frequency of bits
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] frequencies = new int[ALPH_SIZE+1];
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			frequencies[val] = frequencies[val]+1;
		}
		frequencies[PSEUDO_EOF] = 1;
		return frequencies;
	}

	/**
	 * Makes a tree from the frequencies using greedy algorithm
	 * @param counts frequencies of the encoding
	 * @return HuffNode the root of the tree
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for(int i = 0; i<counts.length; i++) {
			if(counts[i]!=0) {
				pq.add(new HuffNode(i,counts[i],null,null));
			}
		}
		if(myDebugLevel>= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes\n",pq.size());
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.myWeight+right.myWeight, left, right);
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			pq.add(t);

		}
		return pq.remove();
	}
	/**
	 * Returns the encodings formed from the tree
	 * @param root of the tree
	 * @return String[] with the encoding on how to get to each encoded byte
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		codingHelper(root,"");
		return encodings;
	}

	/**
	 * Helper method that creates the encodings
	 * @param root of the tree
	 * @param direction current direction
	 */
	private void codingHelper(HuffNode root, String direction) {

		if(root.myLeft==null && root.myRight == null) {
			encodings[root.myValue]=direction;
			if(myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", root.myValue, direction );
			}
		}
		else {
			codingHelper(root.myLeft, direction+"0");
			codingHelper(root.myRight,direction+"1");
		}
	}

	/**
	 * Writes the header to the output
	 * @param root of the tree
	 * @param out BitOutputStream
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft==null&&root.myRight==null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
			return;

		}
		out.writeBits(1, 0);
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		int val = in.readBits(BITS_PER_WORD);	
		while(val!=-1) {
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			val = in.readBits(BITS_PER_WORD);
		}
		out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF],2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits!=HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}

	/**
	 * Checks if there is the correct header
	 * @param in BitInputStream
	 * @return HuffNode
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("illegal header starts with "+bit);
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
	}

	/**
	 * Reads the compressed bits and writes to file
	 * @param root of the tree
	 * @param in BitInputStream
	 * @param out BitOutputStream
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else { 
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft==null && current.myRight==null) {
					if (current.myValue == PSEUDO_EOF) 
						break;   // out of loop
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
	}

}