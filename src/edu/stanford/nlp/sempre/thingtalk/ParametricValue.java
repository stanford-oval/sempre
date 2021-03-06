package edu.stanford.nlp.sempre.thingtalk;

import java.util.*;

import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.Values;
import fig.basic.LispTree;

/**
 * Base class for thingtalk entities that take parameters (actions, triggers,
 * queries)
 * 
 * @author Rakesh Ramesh & Giovanni Campagna
 */
public abstract class ParametricValue extends Value implements Cloneable {
  public final TypedStringValue person; // null if its me or else value supplied
  public final ChannelNameValue name;
  public ArrayList<ParamValue> params;
  public ArrayList<ArrayList<ParamValue>> predicate;

  public ParametricValue(LispTree tree) {
    this.person = (TypedStringValue) Values.fromLispTree(tree.child(1));

    int index = 2;
    if(this.person == null) index = 1;
    this.name = (ChannelNameValue) Values.fromLispTree(tree.child(index));

    LispTree paramTree = tree.child(index+1);
    this.params = new ArrayList<>();
    for (int i = 1; i < paramTree.children.size(); i++) {
      this.params.add(((ParamValue) Values.fromLispTree(paramTree.child(i))));
    }

    LispTree predTree = tree.child(index+2);
    this.predicate = new ArrayList<>();
    for(int i=1; i < predTree.children.size(); i++) {
      this.predicate.add(new ArrayList<>());
      LispTree orPredTree = predTree.child(i);
      for(int j=0; j < orPredTree.children.size(); j++) {
        this.predicate.get(i-1).add((ParamValue) Values.fromLispTree(orPredTree.child(j)));
      }
    }
  }

  public ParametricValue(ChannelNameValue name, List<ParamValue> params) {
    this.name = name;
    this.person = null;

    this.params = new ArrayList<>();
    this.params.addAll(params);

    this.predicate = new ArrayList<>();
  }

  public ParametricValue(TypedStringValue person, ChannelNameValue name) {
    this.name = name;
    this.person = person;

    this.params = new ArrayList<>();

    this.predicate = new ArrayList<>();
  }

  public ParametricValue(ChannelNameValue name) {
    this.name = name;
    this.person = null;

    this.params = new ArrayList<>();

    this.predicate = new ArrayList<>();
  }

  protected abstract String getLabel();

  public void addParam(ParamValue param) {
    assert (params != null) : param;
    params.add(param);
  }

  public void addPredicate(ParamValue condition, boolean isNewConjunction) {
    assert (predicate != null) : condition;
    if(isNewConjunction) {
      predicate.add(new ArrayList<>());
    }

    assert (predicate.size() > 0) : condition;
    predicate.get(predicate.size()-1).add(condition);
  }

  public boolean hasParamName(String name) {
    for (ParamValue p : params) {
      if (p.name.argname.equals(name))
        return true;
    }

    return false;
  }

  public boolean isEmptyPredicate() {
    return (predicate.size() == 0);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(getLabel());
    if(person != null) tree.addChild(person.toLispTree());
    tree.addChild(name.toLispTree());

    LispTree paramTree = LispTree.proto.newList();
    paramTree.addChild("params");
    for (ParamValue param : this.params)
      paramTree.addChild(param.toLispTree());
    tree.addChild(paramTree);

    LispTree predicateTree = LispTree.proto.newList();
    predicateTree.addChild("predicate");
    for (ArrayList<ParamValue> orPredList : this.predicate) {
      LispTree orPredTree = LispTree.proto.newList();
      for (ParamValue condition : orPredList) {
        orPredTree.addChild(condition.toLispTree());
      }
      predicateTree.addChild(orPredTree);
    }
    tree.addChild(predicateTree);

    return tree;
  }

  @Override
  public Map<String, Object> toJson() {
    Map<String, Object> json = new HashMap<>();

    if(this.person != null) json.put("person", person.value);
    json.put("name", name.toJson());

    List<Object> args = new ArrayList<>();
    json.put("args", args);
    for (ParamValue param : params) {
      args.add(param.toJson());
    }

    List<List<Object>> predArray = new ArrayList<>();
    json.put("predicate", predArray);
    for(ArrayList<ParamValue> orPredList : predicate) {
      List<Object> orConditionArray = new ArrayList<>();
      predArray.add(orConditionArray);
      for(ParamValue condition : orPredList) {
        orConditionArray.add(condition.toJson());
      }
    }

    return json;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParametricValue that = (ParametricValue) o;

    if (person != null ? !person.equals(that.person) : that.person != null) return false;
    if (!name.equals(that.name)) return false;
    if (!params.equals(that.params)) return false;
    return predicate.equals(that.predicate);
  }

  @Override
  public int hashCode() {
    int result = person != null ? person.hashCode() : 0;
    result = 31 * result + name.hashCode();
    result = 31 * result + params.hashCode();
    result = 31 * result + predicate.hashCode();
    return result;
  }

  @Override
  public ParametricValue clone() {
    try {
      ParametricValue self = (ParametricValue) super.clone();
      self.params = new ArrayList<>(this.params);
      self.predicate = new ArrayList<>();
      for (ArrayList<ParamValue> orPredList : this.predicate) {
        self.predicate.add(new ArrayList<>(orPredList));
      }
      return self;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
