(ns checkv.marshal
  (:require [clojure.set :as set]))

(def item-key->attr
  {:tags :item/tags
   :collapsed :item/collapsed
   :content :item/content
   :due :item/due
   :assignee_ids :item/assignee-ids
   :tags_as_text :item/tags-as-text
   :tasks :item/tasks
   :updated_at :item/updated-at
   :link_ids :item/linked-items
   :due_user_ids :item/due-user-ids
   :details :item/details
   :status :item/status
   :parent_id :item/parent-item
   :id :item/id
   :comments_count :item/comments-count
   :backlink_ids :item/backlinks
   :position :item/position
   :update_line :item/update-line
   :checklist_id :item/list
   :deleted :item/deleted})

(def list-key->attr
  {:archived :list/archived
   :tags :list/tags
   :related_task_ids :list/related-task-ids
   :name :list/name
   :user_updated_at :list/user-updated-at
   :public :list/public
   :item_count :list/item-count
   :tags_as_text :list/tags-as-text
   :updated_at :list/updated-at
   :read_only :list/read-only
   :id :list/id
   :user_count :list/user-count
   :task_count :list/task-count
   :options :list/options
   :percent_completed :list/percent-completed
   :task_completed :list/task-completed
   :markdown? :list/markdown?
   :created_at :list/created-at})

(defn ->ref-id
  [attr]
  (fn [v] {attr v}))

(defn ->ref-ids
  [attr]
  (fn [vs] (mapv #(hash-map attr %) vs)))

(defn item-doc->txn-ent
  [item]
  [:xtdb.api/put (-> item
                     (set/rename-keys item-key->attr)
                     (assoc :xt/id {:item/id (:id item)})
                     (dissoc :item/position) ; mutation doesn't change ..
                     (dissoc :item/backlinks) ; .. updated_at
                     (update :item/tags #(vec (keys %)))
                     (update :item/linked-items (->ref-ids :item/id))
                     (update :item/parent-item (->ref-id :item/id))
                     (update :item/list (->ref-id :list/id)))])

(defn list-doc->txn-ent
  [list-doc]
  [:xtdb.api/put (-> list-doc
                     (set/rename-keys list-key->attr)
                     (assoc :xt/id {:list/id (:id list-doc)})
                     (update :list/tags #(vec (keys %))))])

