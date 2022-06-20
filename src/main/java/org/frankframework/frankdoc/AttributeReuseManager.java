package org.frankframework.frankdoc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.frankframework.frankdoc.model.FrankAttribute;
import org.frankframework.frankdoc.util.XmlBuilder;

class AttributeReuseManager {
	private static class AttributeToBuild {
		FrankAttribute attribute;
		XmlBuilder group;
		boolean reused = true;

		AttributeToBuild(FrankAttribute attribute, XmlBuilder group) {
			this.attribute = attribute;
			this.group = group;
		}
	}

	private static class AttributeToInsert {
		XmlBuilder attributeBuilder;
		XmlBuilder parentBuilder;

		AttributeToInsert(XmlBuilder attributeBuilder, XmlBuilder parentBuilder) {
			this.attributeBuilder = attributeBuilder;
			this.parentBuilder = parentBuilder;
		}
	}

	private static class AttributeToBuildGroup {
		private List<AttributeToBuild> itemsThatShareAttribute = new ArrayList<>();

		AttributeToBuildGroup(AttributeToBuild first) {
			itemsThatShareAttribute.add(first);
		}

		void add(AttributeToBuild item) {
			if(item.attribute != itemsThatShareAttribute.get(0).attribute) {
				throw new IllegalArgumentException("Programming error, see stack trace");
			}
			itemsThatShareAttribute.add(item);
		}

		boolean isAttributeHasUniqueGroup() {
			return itemsThatShareAttribute.size() == 1;
		}

		String getAttributeName() {
			return itemsThatShareAttribute.get(0).attribute.getName();
		}

		Stream<AttributeToBuild> getMemberStream() {
			return new ArrayList<>(itemsThatShareAttribute).stream();
		}

		FrankAttribute getCommonFrankAttribute() {
			return itemsThatShareAttribute.iterator().next().attribute;
		}

		void setNoItemReuses() {
			itemsThatShareAttribute.stream()
				.forEach(item -> item.reused = false);
				
		}
	}

	private static class AttributeToBuildGroupGroup {
		private Map<FrankAttribute, AttributeToBuildGroup> groupsSharingAttributeName = new LinkedHashMap<>();

		AttributeToBuildGroupGroup(AttributeToBuild first) {
			AttributeToBuildGroup group = new AttributeToBuildGroup(first);
			groupsSharingAttributeName.put(first.attribute, group);
		}

		void add(AttributeToBuild item) {
			if(! item.attribute.getName().equals(groupsSharingAttributeName.values().iterator().next().getAttributeName())) {
				throw new IllegalArgumentException("Programming error, see stack trace");
			}
			if(groupsSharingAttributeName.containsKey(item.attribute)) {
				groupsSharingAttributeName.get(item.attribute).add(item);
			} else {
				AttributeToBuildGroup group = new AttributeToBuildGroup(item);
				groupsSharingAttributeName.put(item.attribute, group);
			}
		}

		List<AttributeToBuildGroup> getAttributeToBuildGroupsWithOneGroup() {
			return groupsSharingAttributeName.values().stream()
					.filter(AttributeToBuildGroup::isAttributeHasUniqueGroup)
					.collect(Collectors.toList());
		}

		boolean hasMultipleGroupsForReuse() {
			return groupsSharingAttributeName.values().stream()
					.filter(g -> ! g.isAttributeHasUniqueGroup())
					.map(AttributeToBuildGroup::getCommonFrankAttribute)
					.distinct()
					.collect(Collectors.counting()) == 1L;
		}

		void setNoItemReuses() {
			groupsSharingAttributeName.values().stream()
				.flatMap(AttributeToBuildGroup::getMemberStream)
				.forEach(item -> item.reused = false);
				
		}
	}

	private List<Object> items = new ArrayList<>();
	private Map<String, AttributeToBuildGroupGroup> groupedAttributesToBuild = new LinkedHashMap<>();
	private Set<FrankAttribute> definedReusableAttributes = new HashSet<>();

	void addAttribute(FrankAttribute attribute, XmlBuilder group) {
		AttributeToBuild item = new AttributeToBuild(attribute, group);
		items.add(item);
		if(groupedAttributesToBuild.containsKey(attribute.getName())) {
			groupedAttributesToBuild.get(attribute.getName()).add(item);
		} else {
			groupedAttributesToBuild.put(attribute.getName(), new AttributeToBuildGroupGroup(item));
		}
	}

	void addAttribute(XmlBuilder attributeBuilder, XmlBuilder group) {
		items.add(new AttributeToInsert(attributeBuilder, group));
	}

	void buildAttributes(AttributeReuseManagerCallback callback) {
		classifyAttributesToBuild();
		buildClassifiedAttributes(callback);
	}

	private void classifyAttributesToBuild() {
		groupedAttributesToBuild.values().stream().forEach(this::classifyBucket);
	}

	private void classifyBucket(AttributeToBuildGroupGroup group) {
		if(group.hasMultipleGroupsForReuse()) {
			group.setNoItemReuses();
		} else {
			group.getAttributeToBuildGroupsWithOneGroup().forEach(AttributeToBuildGroup::setNoItemReuses);
		}
	}

	private void buildClassifiedAttributes(AttributeReuseManagerCallback callback) {
		for(Object item: items) {
			if(item instanceof AttributeToBuild) {
				buildAttribute((AttributeToBuild) item, callback);
			} else {
				AttributeToInsert attributeToInsert = (AttributeToInsert) item;
				attributeToInsert.parentBuilder.addSubElement(attributeToInsert.attributeBuilder);
			}
		}
	}

	private void buildAttribute(AttributeToBuild attributeToBuild, AttributeReuseManagerCallback callback) {
		if(attributeToBuild.reused) {
			if(! definedReusableAttributes.contains(attributeToBuild.attribute)) {
				callback.addReusableAttribute(attributeToBuild.attribute);
				definedReusableAttributes.add(attributeToBuild.attribute);
			}
			callback.addReusedAttributeReference(attributeToBuild.attribute, attributeToBuild.group);
		} else {
			callback.addAttributeInline(attributeToBuild.attribute, attributeToBuild.group);
		}		
	}
}
