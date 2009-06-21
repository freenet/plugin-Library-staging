/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import plugins.Interdex.util.Serialiser.SerialiseTask;
import plugins.Interdex.util.Serialiser.InflateTask;
import plugins.Interdex.util.Serialiser.DeflateTask;

/**
** A {@link SkeletonMap} of a {@link PrefixTreeMap}.
**
** @author infinity0
*/
public class SkeletonPrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V>
implements SkeletonMap<K, V> {

	/**
	** Represents a PrefixTreeMap which has not been loaded, but which a parent
	** SkeletonPrefixTreeMap (that has been loaded) refers to.
	**
	** TODO make this contain a DummyValue
	**
	** @author infinity0
	*/
	public static class DummyPrefixTreeMap<K extends PrefixKey, V> extends PrefixTreeMap<K, V> {

		public DummyPrefixTreeMap(K p, int len, int maxsz, SkeletonPrefixTreeMap<K, V> par) {
			super(p, len, maxsz, par);
		}

		public DummyPrefixTreeMap(K p, int maxsz) {
			super(p, 0, maxsz, null);
		}

		public DummyPrefixTreeMap(K p) {
			super(p, 0, p.symbols(), null);
		}

		Object dummy;

		/************************************************************************
		 * public class PrefixTree
		 ************************************************************************/

		protected DummyPrefixTreeMap<K, V> selectNode(int i) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected TreeMap<K, V> getLocalMap() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected void clearLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected Set<K> keySetLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		protected int sizeLocal() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		/************************************************************************
		 * public interface Map
		 ************************************************************************/

		public boolean containsValue(Object o) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Set<Map.Entry<K,V>> entrySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public boolean isEmpty() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Set<K> keySet() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

		public Collection<V> values() {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		}

	}

	/**
	** The constructor points this to the same object as tmap, so we don't have
	** to keep casting tmap when we want to access SkeletonTreeMap methods.
	*/
	final SkeletonTreeMap<K, V> itmap;

	public SkeletonPrefixTreeMap(K p, int len, int maxsz, int sz, int subs, int[] szPre, boolean[] chd, K[] keys, V[] values, SkeletonPrefixTreeMap<K, V> par) {
		this(p, len, maxsz, par);

		// check size == sum { sizePrefix }
		int s = 0;
		for (int pre: szPre) { s += pre; }
		if (sz != s) {
			throw new IllegalArgumentException("Invariant broken: size == sum{ sizePrefix }");
		}

		size = sz;
		subtrees = subs;
		for (int i=0; i<szPre.length; ++i) {
			sizePrefix[i] = szPre[i];
		}

		putDummyChildren(chd);
		putDummySubmap(keys, values);
	}

	public SkeletonPrefixTreeMap(K p, int len, int maxsz, SkeletonPrefixTreeMap<K, V> par) {
		super(p, len, maxsz, new SkeletonTreeMap<K, V>(), (PrefixTreeMap<K, V>[])new PrefixTreeMap[p.symbols()], par);
		itmap = (SkeletonTreeMap<K, V>)tmap;
	}

	public SkeletonPrefixTreeMap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public SkeletonPrefixTreeMap(K p) {
		this(p, 0, p.symbols(), null);
	}

	protected Serialiser<SkeletonPrefixTreeMap<K, V>> serialiser;
	protected Serialiser<SkeletonTreeMap<K, V>> serialiserLocal;
	protected Serialiser<V> serialiserLocalValue;

	public void setSerialiser(Serialiser<SkeletonPrefixTreeMap<K, V>> s, Serialiser<SkeletonTreeMap<K, V>> ls, Serialiser<V> vs) {
		serialiser = s;
		serialiserLocal = ls;
		serialiserLocalValue = vs;
		itmap.setSerialiser(vs);
		for (PrefixTreeMap<K, V> ch: child) {
			if (ch != null && ch instanceof SkeletonPrefixTreeMap) {
				((SkeletonPrefixTreeMap<K, V>)ch).setSerialiser(s, ls, vs);
			}
		}
	}


	/**
	** Puts DummyPrefixTreeMap objects into the child array.
	**
	** @param chd An array of booleans indicating whether to attach a dummy
	** @throws ArrayIndexOutOfBoundsException when the input array is smaller
	**         than the child array
	*/
	protected void putDummyChildren(boolean[] chd) {

		// check count{ i : chd[i] == true } == subtrees
		int s = 0;
		for (boolean b: chd) {
			if (b) { ++s; }
		}
		if (subtrees != s) {
			throw new IllegalArgumentException("Invariant broken: subtrees == count{ non-null children }");
		}

		// check that there exists sz such that for all i:
		// (chd[i] == false) => sizePrefix[i] <= sz
		// (chd[i] != false) => sizePrefix[i] >= sz
		int sz = sizeMax;
		// find the size of the smallest child. if the smallest child is larger
		// than maxSize, then maxSize will be returned instead, but this makes no
		// difference to the tests below (think about it...)
		for (int i=0; i<child.length; ++i) {
			if (chd[i]) {
				if (sizePrefix[i] < sz) {
					sz = sizePrefix[i];
				}
			}
		}
		// see if there are any non-child prefix groups larger than the smallest
		// child. whilst we're at it, calculate sum{ sizePrefix[j]: !chd[j] }
		// for the next test.
		s = 0;
		for (int i=0; i<child.length; ++i) {
			if (!chd[i]) {
				s += sizePrefix[i];
				if (sizePrefix[i] > sz) {
					throw new IllegalArgumentException(
					"Invariant broken: there exists sz such that for all i: " +
					"(child[i] == null) => sizePrefix[i] <= sz and " +
					"(child[i] != null) => sizePrefix[i] >= sz"
					);
				}
			}
		}

		// check that sum{ sizePrefix[j] : !chd[j] } + subtrees + sz > sizeMax
		if (s + subtrees + sz <= sizeMax) {
			throw new IllegalArgumentException("Invariant broken: count{ non-child prefix groups } + subtrees + sz > maxSize");
		}

		for (int i=0; i<child.length; ++i) {
			if (chd[i]) { putDummyChild(i); }
		}
	}

	/**
	** Put a DummyPrefixTreeMap object into the child array.
	**
	** @param i The index to attach the dummy to
	*/
	protected void putDummyChild(int i) {
		child[i] = new DummyPrefixTreeMap((K)prefix.spawn(preflen, i), preflen+1, sizeMax, this);
	}

	/**
	** Put dummy mappings onto the submap. This method carries out certain
	** tests which assume that the child array has already been populated by
	** putDummyChildren.
	**
	** @param keys The array of keys of the map
	** @param values The array of values of the map
	*/
	protected void putDummySubmap(K[] keys, V[] values) {
		if (keys.length != values.length) {
			throw new IllegalArgumentException("keys/values length mismatch");
		}

		// check that keys agrees with child[i]
		int[] szPre = new int[sizePrefix.length];
		for (int i=0; i<keys.length; ++i) {
			int p = keys[i].get(preflen);
			if (child[p] != null) {
				throw new IllegalArgumentException("A subtree already exists for this key: " + keys[i]);
			}
			++szPre[p];
		}

		// check keys.length == sum{ sizePrefix[j] : child[j] == null } and that
		// keys agrees with sizePrefix
		int sz = 0;
		for (int i=0; i<sizePrefix.length; ++i) {
			if (child[i] == null) {
				sz += szPre[i];
				if (sizePrefix[i] != szPre[i]) {
					throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
				}
			}
		}
		if (sz != keys.length) {
			throw new IllegalArgumentException("The keys given contradicts the sizePrefix array");
		}

		for (int i=0; i<keys.length; ++i) {
			itmap.putDummy(keys[i], values[i]);
		}
	}

	/**
	** Assimilate an existing SkeletonPrefixTreeMap into this one. The prefix
	** must match and there must already be a DummyPrefixTreeMap in its place
	** in the child array.
	**
	** @param subtree The tree to assimilate
	*/
	public void assimilate(SkeletonPrefixTreeMap<K, V> t) {
		if (t.preflen <= preflen) {
			throw new IllegalArgumentException("Only subtrees can be spliced onto an SkeletonPrefixTreeMap.");
		}
		if (!t.prefix.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = t.prefix.get(preflen);

		if (child[i] == null) {
			throw new IllegalArgumentException("This tree does not have a subtree with prefix " + t.prefix);
		}
		// check t.size == sizePrefix[i]
		if (t.size != sizePrefix[i]) {
			throw new IllegalArgumentException("The size of this tree contradicts the parent's sizePrefix");
		}

		if (child[i] instanceof DummyPrefixTreeMap) {
			child[i] = t;
		} else if (child[i] instanceof SkeletonPrefixTreeMap) {
			if (t.preflen > child[i].preflen) {
				((SkeletonPrefixTreeMap)child[i]).assimilate(t);
			} else {
				// t.preflen == child.preflen since t.preflen > this.preflen
				throw new IllegalArgumentException("This tree has already assimilated a subtree with prefix " + t.prefix);
			}
		}
	}

	/************************************************************************
	 * public class PrefixTree
	 ************************************************************************/

	// We override this method so that the correct serialiser is set
	protected PrefixTreeMap<K, V> makeSubTree(int msym) {
		SkeletonPrefixTreeMap<K, V> ch = new SkeletonPrefixTreeMap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, sizeMax, this);
		ch.setSerialiser(serialiser, serialiserLocal, serialiserLocalValue);
		return ch;
	}

	protected Map<K, V> selectNode(int i) {
		if (child[i] == null) {
			return tmap;
		} else if (child[i] instanceof DummyPrefixTreeMap) {
			throw new DataNotLoadedException("PrefixTreeMap not loaded for " + prefix.toString(), this.parent, this.prefix, this);
		} else {
			return child[i];
		}
	}

	/************************************************************************
	 * public interface SkeletonMap
	 ************************************************************************/

	public boolean isFull() {
		// TODO use a counter to optimise this
		if (!itmap.isFull()) { return false; }
		for (PrefixTreeMap t: child) {
			if (t instanceof DummyPrefixTreeMap) { return false; }
			if (t instanceof SkeletonPrefixTreeMap && !((SkeletonPrefixTreeMap)t).isFull()) {
				return false;
			}
		}
		return true;
	}

	public boolean isBare() {
		// TODO use a counter to optimise this
		if (!itmap.isBare()) { return false; }
		for (PrefixTreeMap t: child) {
			if (t instanceof SkeletonPrefixTreeMap) { return false; }
		}
		return true;
	}

	public Map<K, V> complete() {
		if (!isFull()) {
			throw new DataNotLoadedException("PrefixTreeMap not fully loaded for " + prefix.toString(), this, this);
		} else {
			TreeMap<K, V> ntmap = (TreeMap<K, V>)itmap.complete();
			PrefixTreeMap<K, V>[] nchild = (PrefixTreeMap<K, V>[])new PrefixTreeMap[subtreesMax];

			for (int i=0; i<subtreesMax; ++i) {
				if (child[i] != null) {
					nchild[i] = (PrefixTreeMap<K, V>)((SkeletonPrefixTreeMap<K, V>)child[i]).complete();
				}
			}

			return new PrefixTreeMap(prefix, preflen, sizeMax, ntmap, nchild, null);
		}
	}

	public void inflate() {
		throw new UnsupportedOperationException("Not implemented.");
		//assert(isFull());
	}

	public void deflate() {
		if (!itmap.isBare()) { itmap.deflate(); }
		DeflateTask<SkeletonTreeMap<K, V>> de = serialiserLocal.newDeflateTask(itmap);
		de.setOption(prefixString());
		de.start();
		de.join();
		// TODO: maybe store de.get() somewhere. it will be necessary when we
		// implement an algorithm to merge small TreeMaps into one file

		for (int i=0; i<subtreesMax; ++i) {
			if (child[i] != null && child[i] instanceof SkeletonPrefixTreeMap) {
				SkeletonPrefixTreeMap<K, V> ch = (SkeletonPrefixTreeMap<K, V>)child[i];
				if (!ch.isBare()) { ch.deflate(); }
				// TODO: parallelise this, turn it into DeflateTask
				Object o = serialiser.deflate(ch);
				// TODO: make this use the dummy object returned
				putDummyChild(i);
			}
		}
		assert(isBare());
	}

	public void inflate(SkeletonMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void deflate(SkeletonMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void inflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void deflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}


	/**
	** A {@link Serialiser} that has access to all the fields of this class.
	*/
	abstract public static class PrefixTreeMapSerialiser<K extends PrefixKey, V> implements Serialiser<SkeletonPrefixTreeMap<K, V>> {

		public SkeletonPrefixTreeMap<K, V> inflate(Object dummy) {
			throw new UnsupportedOperationException("Not implemented.");
		}

		public Object deflate(SkeletonPrefixTreeMap<K, V> skel) {
			if (!skel.isBare()) {
				throw new IllegalArgumentException("Object passed to deflate is not bare.");
			}
			DeflateTask de = newDeflateTask(skel);
			de.start(); de.join();
			return de.get();
		}

		/**
		** Add all the data from a skeleton map to the given {@link DeflateTask}.
		** It is recommended that this method be called in the constructor of the
		** {@link DeflateTask} being passed in.
		**
		** @param de The task to receive the data.
		** @param skel The skeleton to add to the task.
		*/
		protected void putAll(DeflateTask de, SkeletonPrefixTreeMap<K, V> skel) {
			de.put("prefix", skel.prefix.toString());
			de.put("preflen", skel.preflen);
			de.put("sizeMax", skel.sizeMax);
			de.put("size", skel.size);
			de.put("subtreesMax", skel.subtreesMax);
			de.put("subtrees", skel.subtrees);
			de.put("sizePrefix", skel.sizePrefix);

			boolean chd[] = new boolean[skel.subtreesMax];
			for (int i=0; i<skel.subtreesMax; ++i) { chd[i] = (skel.child[i] != null); }
			de.put("_child", chd);

			Set<K> keySet = skel.itmap.keySet();
			String[] keys = new String[keySet.size()];
			int i=0; for (K k: keySet) { keys[i++] = k.toString(); }
			de.put("_tmap", keys);
		}

	}

}
