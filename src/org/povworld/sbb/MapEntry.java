package org.povworld.sbb;

import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

public class MapEntry<K, V> implements Map.Entry<K, V> {
	private final K key;
	private final V value;
	
	public MapEntry(K key, V value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}

	@Override
	public V setValue(V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof Entry)) {
			return false;
		}
		Entry<?,?> other = (Entry<?,?>) obj;
		return Objects.equals(key, other.getKey()) && Objects.equals(value, other.getValue());
	}
	
	@Override
	public String toString() {
		return key + "="+value;
	}
}