<template>
  <main
    class="post-editor-page"
    @paste.capture="paste"
  >
    <LiquidEther />
    <form
      class="post-editor-card"
      novalidate
      @submit.prevent="submit(true)"
    >
      <div
        class="editor-decorations"
        aria-hidden="true"
      >
        <span class="editor-sticker sticker-writing">{{ t('editor.stickers.writing') }}</span>
        <span class="editor-sticker sticker-cheer">{{ t('editor.stickers.cheer') }}</span>
        <span class="editor-sticker sticker-smile">{{ t('editor.stickers.smile') }}</span>
        <svg
          v-for="bubble in 4"
          :key="'bubble-'+bubble"
          :class="['editor-bubble','bubble-'+bubble]"
          viewBox="0 0 40 40"
        >
          <circle
            cx="20"
            cy="20"
            r="17"
          />
          <path d="M11 17a10 10 0 0 1 6-6" />
        </svg>
        <svg
          v-for="flower in 2"
          :key="'flower-'+flower"
          :class="['editor-flower','flower-'+flower]"
          viewBox="0 0 24 24"
        >
          <ellipse
            v-for="angle in [0,72,144,216,288]"
            :key="angle"
            cx="12"
            cy="6.5"
            rx="3.2"
            ry="5"
            :transform="'rotate('+angle+' 12 12)'"
          />
          <circle
            cx="12"
            cy="12"
            r="1.8"
          />
        </svg>
        <svg
          v-for="star in 2"
          :key="'star-'+star"
          :class="['editor-star','star-'+star]"
          viewBox="0 0 24 24"
        >
          <path d="M12 2c.8 6 4 9.2 10 10-6 .8-9.2 4-10 10-.8-6-4-9.2-10-10 6-.8 9.2-4 10-10z" />
        </svg>
      </div>
      <div
        class="editor-greeting"
        aria-hidden="true"
      >
        <img
          :src="articleEditAvatar"
          alt=""
        >
        <span>{{ t('editor.greeting') }}</span>
      </div>
      <header class="editor-header">
        <h1>{{ t('editor.titleLead') }} <span>{{ t('editor.titleAccent') }}</span><el-icon><MagicStick /></el-icon></h1><router-link :to="{name:RouteName.HOME}">
          <el-icon><ArrowLeft /></el-icon>{{ t('editor.home') }}
        </router-link>
      </header>
      <div
        v-if="loading||loadError"
        class="draft-state"
        role="status"
      >
        <p>{{ t(loading?'editor.loading':'editor.error.draftLoad') }}</p><el-button
          v-if="loadError"
          @click="loadDraft"
        >
          {{ t('home.retry') }}
        </el-button>
      </div>
      <fieldset :disabled="busy||loading||loadError">
        <div class="editor-top-row">
          <div class="category-field">
            <strong>{{ t('editor.category') }}</strong><div class="editor-categories">
              <button
                v-for="category in Categories"
                :key="category"
                type="button"
                :class="['category-'+category.toLowerCase(),{selected:form.category===category}]"
                :aria-pressed="form.category===category"
                @click="form.category=category"
              >
                <span />{{ t('home.categories.'+category) }}
              </button>
            </div>
          </div>
          <div class="title-field">
            <label for="post-title">{{ t('editor.title') }}</label><div
              class="input-shell"
              :class="{invalid:errors.title}"
            >
              <input
                id="post-title"
                v-model="form.title"
                :placeholder="t('editor.titlePlaceholder')"
                :aria-invalid="Boolean(errors.title)"
                @input="errors.title=''"
              ><small class="title-counter">{{ t('editor.counters',{count:titleCount,max:PostEditor.TITLE_MAX}) }}</small><span
                v-if="errors.title"
                class="field-error"
                role="alert"
              >{{ errors.title }}</span>
            </div>
          </div>
        </div>
        <div class="editor-columns">
          <section class="body-panel">
            <div class="panel-heading">
              <h2>{{ t('editor.body') }}</h2><div class="body-count">
                <el-progress
                  :percentage="Math.min(100,bodyCount/PostEditor.BODY_MAX*100)"
                  :show-text="false"
                  color="var(--meng-primary)"
                /><small>{{ t('editor.counters',{count:bodyCount,max:PostEditor.BODY_MAX}) }}</small>
              </div>
            </div>
            <div
              class="body-shell"
              :class="{invalid:errors.body}"
            >
              <div class="editor-toolbar">
                <div class="format-tools">
                  <button
                    v-for="format in formats"
                    :key="format.key"
                    type="button"
                    :disabled="preview"
                    :aria-label="t('editor.formats.'+format.key)"
                    :title="t('editor.formats.'+format.key)"
                    @click="editor?.format(format.key)"
                  >
                    <el-icon v-if="format.icon">
                      <component :is="format.icon" />
                    </el-icon>
                    <span
                      v-else
                      :class="'format-'+format.key"
                    >{{ t('editor.formatMarks.'+format.key) }}</span>
                  </button>
                </div><button
                  type="button"
                  class="ai-tool"
                  @click="unavailable"
                >
                  <el-icon><MagicStick /></el-icon>{{ t('editor.inspiration') }}
                </button><div
                  class="editor-modes"
                  :class="{preview}"
                >
                  <button
                    type="button"
                    :aria-pressed="!preview"
                    @click="preview=false"
                  >
                    {{ t('editor.edit') }}
                  </button><button
                    type="button"
                    :aria-pressed="preview"
                    @click="preview=true"
                  >
                    {{ t('editor.preview') }}
                  </button>
                </div>
              </div>
              <div class="body-content">
                <MarkdownEditor
                  v-show="!preview"
                  ref="editor"
                  v-model="form.content"
                  :disabled="busy||loading||loadError"
                /><div
                  v-if="preview"
                  class="preview-scroll"
                >
                  <MarkdownBody
                    v-if="form.content"
                    :content="form.content"
                  /><p
                    v-else
                    class="preview-empty"
                  >
                    {{ t('editor.noContent') }}
                  </p>
                </div><span
                  v-if="!preview&&!form.content"
                  class="editor-placeholder"
                >{{ t('editor.bodyPlaceholder') }}</span><span
                  v-if="errors.body"
                  class="field-error"
                  role="alert"
                >{{ errors.body }}</span>
              </div>
            </div>
          </section>
          <aside class="editor-sidebar">
            <section
              class="media-panel editor-panel"
              @dragover.prevent
              @drop="drop"
            >
              <div class="panel-heading">
                <div
                  class="media-tabs"
                  :class="{video}"
                >
                  <button
                    type="button"
                    :class="{selected:!video}"
                    :aria-pressed="!video"
                    @click="video=false"
                  >
                    <el-icon><PictureIcon /></el-icon>{{ t('editor.images') }}
                  </button><button
                    type="button"
                    :class="{selected:video}"
                    :aria-pressed="video"
                    @click="video=true"
                  >
                    <el-icon><VideoCamera /></el-icon>{{ t('editor.video') }}
                  </button>
                </div><small>{{ t('editor.counters',{count:images.length,max:PostEditor.IMAGE_MAX}) }}</small>
              </div>
              <div class="media-content">
                <Transition name="media-swap">
                  <button
                    v-if="video"
                    class="video-placeholder"
                    type="button"
                    @click="unavailable"
                  >
                    <el-icon><VideoCamera /></el-icon><span>{{ t('editor.videoPlaceholder') }}</span>
                  </button>
                  <div
                    v-else
                    class="image-grid"
                  >
                    <template
                      v-for="slot in slots"
                      :key="slot.item?.key||slot.index"
                    >
                      <div
                        v-if="slot.item"
                        class="image-slot"
                        :class="{failed:slot.item.status==='failed'}"
                      >
                        <img
                          :src="slot.item.preview"
                          :alt="t('editor.imageAlt')"
                        ><button
                          type="button"
                          class="remove-image"
                          :aria-label="t('editor.removeImage')"
                          @click="removeImage(slot.item)"
                        >
                          <el-icon><Close /></el-icon>
                        </button><button
                          v-if="slot.item.status==='failed'"
                          class="retry-image"
                          type="button"
                          :aria-label="t('editor.retryImage')"
                          :title="slot.item.error"
                          @click="retryImage(slot.item)"
                        >
                          <el-icon><Refresh /></el-icon>
                        </button><el-progress
                          v-else-if="slot.item.status!=='ready'"
                          class="upload-progress"
                          :percentage="slot.item.progress"
                          :show-text="false"
                        />
                      </div><button
                        v-else-if="slot.add"
                        type="button"
                        class="image-slot add-image"
                        :aria-label="t('editor.addImage')"
                        @click="imageInput.click()"
                      >
                        <el-icon><Plus /></el-icon>
                      </button><div
                        v-else
                        class="image-slot empty-image"
                        aria-hidden="true"
                      >
                        <el-icon><PictureIcon /></el-icon>
                      </div>
                    </template>
                  </div>
                </Transition>
              </div>
              <p
                v-if="uploadNotice||errors.images"
                class="upload-error"
                role="alert"
              >
                {{ uploadNotice||errors.images }}
              </p><input
                ref="imageInput"
                class="file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                multiple
                @change="picked($event)"
              >
            </section>
            <section
              class="cover-panel editor-panel"
              @dragover.prevent
              @drop="drop($event,true)"
            >
              <div class="panel-heading">
                <h2>{{ t('editor.cover') }}</h2><button
                  type="button"
                  class="ai-tool"
                  @click="unavailable"
                >
                  <el-icon><MagicStick /></el-icon>{{ t('editor.generateCover') }}
                </button>
              </div><div class="cover-box">
                <button
                  class="choose-cover"
                  type="button"
                  :aria-label="t('editor.coverPlaceholder')"
                  @click="coverInput.click()"
                >
                  <img
                    v-if="coverPreview"
                    :src="coverPreview.preview"
                    :alt="t('editor.coverAlt')"
                  ><template v-else>
                    <el-icon><Plus /></el-icon><span>{{ t('editor.coverPlaceholder') }}</span>
                  </template><small
                    v-if="!cover&&coverPreview"
                    class="default-cover"
                  >{{ t('editor.defaultCover') }}</small>
                </button><button
                  v-if="cover"
                  type="button"
                  class="remove-image"
                  :aria-label="t('editor.removeCover')"
                  @click="removeImage(cover)"
                >
                  <el-icon><Close /></el-icon>
                </button><button
                  v-if="cover?.status==='failed'"
                  type="button"
                  class="retry-image"
                  :aria-label="t('editor.retryImage')"
                  :title="cover.error"
                  @click="retryImage(cover)"
                >
                  <el-icon><Refresh /></el-icon>
                </button><el-progress
                  v-else-if="cover&&cover.status!=='ready'"
                  class="upload-progress"
                  :percentage="cover.progress"
                  :show-text="false"
                />
              </div><input
                ref="coverInput"
                class="file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                @change="picked($event,true)"
              >
            </section>
            <section class="tags-panel editor-panel">
              <div class="panel-heading">
                <h2>{{ t('editor.tags') }} <small>{{ t('editor.counters',{count:form.tags.length,max:PostEditor.TAG_MAX}) }}</small></h2><button
                  type="button"
                  class="ai-tool"
                  @click="unavailable"
                >
                  <el-icon><MagicStick /></el-icon>{{ t('editor.pickTags') }}
                </button>
              </div><TransitionGroup
                name="tag-chip"
                tag="div"
                class="selected-tags"
              >
                <span
                  v-for="tag in form.tags"
                  :key="tag"
                  class="editor-tag"
                >{{ tag }}<button
                  type="button"
                  :aria-label="t('editor.removeTag',{name:tag})"
                  @click="form.tags.splice(form.tags.indexOf(tag),1)"
                ><el-icon><Close /></el-icon></button></span>
              </TransitionGroup><div
                ref="tagField"
                class="input-shell tag-input"
                :class="{invalid:errors.tags}"
              >
                <input
                  v-model="tagInput"
                  :disabled="form.tags.length>=PostEditor.TAG_MAX"
                  :placeholder="t('editor.tagPlaceholder')"
                  :aria-label="t('editor.tags')"
                  role="combobox"
                  aria-autocomplete="list"
                  aria-controls="editor-tag-options"
                  :aria-expanded="tagMenuVisible"
                  :aria-activedescendant="tagMenuVisible&&highlightedTag>=0?'editor-tag-option-'+highlightedTag:undefined"
                  @focus="tagFocused=true"
                  @blur="tagFocused=false"
                  @input="tagFocused=true"
                  @keydown.down.prevent="navigateTags(1)"
                  @keydown.up.prevent="navigateTags(-1)"
                  @keydown.esc.prevent="tagFocused=false"
                  @keydown.enter.prevent="confirmTag($event)"
                ><span
                  v-if="errors.tags"
                  class="field-error"
                  role="alert"
                >{{ errors.tags }}</span>
              </div><el-popover
                :visible="tagMenuVisible"
                :virtual-ref="tagField"
                virtual-triggering
                :width="tagField?.offsetWidth"
                placement="bottom-start"
                :show-arrow="false"
                popper-class="editor-tag-dropdown"
              >
                <div
                  id="editor-tag-options"
                  class="editor-tag-options"
                  role="listbox"
                  :aria-label="t('editor.tags')"
                >
                  <button
                    v-for="(tag,index) in tagOptions"
                    :id="'editor-tag-option-'+index"
                    :key="tag"
                    type="button"
                    role="option"
                    :aria-selected="highlightedTag===index"
                    @mousedown.prevent
                    @mouseenter="highlightedTag=index"
                    @click="addTag(tag)"
                  >
                    {{ tag }}
                  </button>
                </div>
              </el-popover><div
                class="tag-recommendations"
                aria-hidden="true"
              >
                <span
                  v-for="slot in 3"
                  :key="slot"
                ><el-icon><MagicStick /></el-icon>{{ t('editor.recommendedTagPlaceholder') }}</span>
              </div>
            </section>
          </aside>
        </div>
        <footer class="editor-actions">
          <el-button
            round
            :loading="busy"
            @click="submit(false)"
          >
            <el-icon><EditPen /></el-icon>{{ t('editor.save') }}
          </el-button><el-button
            type="primary"
            round
            native-type="submit"
            :loading="busy"
          >
            <el-icon><Upload /></el-icon>{{ t('editor.publish') }}
          </el-button>
        </footer>
      </fieldset>
    </form>
  </main>
</template>
<script src="./PostEditorView.js"></script>
<style lang="scss" scoped src="./PostEditorView.scss"></style>
