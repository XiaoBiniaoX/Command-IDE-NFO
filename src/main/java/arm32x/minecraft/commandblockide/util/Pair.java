package arm32x.minecraft.commandblockide.util;

/**
 * A simple pair of two values.
 *
 * <p>Minecraft 1.20.2 introduced {@code net.minecraft.util.Pair} with
 * {@code getLeft()}/{@code getRight()}. That class does not exist in 1.20.1,
 * and the DataFixerUpper version bundled with 1.20.1 only offers
 * {@code getFirst()}/{@code getSecond()}, so this class is used instead to
 * keep the original code shape.</p>
 */
public final class Pair<L, R> {
	private final L left;
	private final R right;

	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public L getLeft() {
		return left;
	}

	public R getRight() {
		return right;
	}

	@Override
	public String toString() {
		return "(" + left + ", " + right + ")";
	}
}
