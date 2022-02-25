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

(defn filter-nil-valued
  [ent]
  (into {}
        (filter #(some? (val %)))
        ent))

(defn item-doc->txn-ent
  [item]
  (-> item
      (assoc :db/id (pr-str [:item/id (:id item)]))
      (update :tags (fn [tags]
                      (mapv #(pr-str [:tag %])
                            (keys tags))))
      (update :link_ids (fn [ids]
                          (mapv #(pr-str [:item/id %])
                                ids)))
      (update :parent_id (fn [v]
                           (when (not= v 0)
                             (pr-str [:item/id v]))))
      (update :backlink_ids (fn [ids]
                              (mapv #(pr-str [:item/id %])
                                    ids)))
      (update :checklist_id #(pr-str [:list/id %]))
      (set/rename-keys item-key->attr)
      (dissoc :item/details)
      (dissoc :uploads)
      (dissoc :color)))

(defn list-doc->txn-ent
  [list-doc]
  (-> list-doc
      (assoc :db/id (pr-str [:list/id (:id list-doc)]))
      (update :tags (fn [tags]
                      (mapv #(pr-str [:tag %])
                            (keys tags))))
      (set/rename-keys list-key->attr)
      (filter-nil-valued)
      (dissoc :uploads)
      (dissoc :color)))

