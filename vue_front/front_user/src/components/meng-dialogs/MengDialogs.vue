<template>
  <DialogShell
    :model-value="!!dialog || !!stageNotice"
    :width="dialogWidth"
    :icon="headingIcon"
    :tone="tone"
    :busy="busy"
    class="companion-dialog"
    :class="modalTheme"
    @close="close"
  >
    <template #title>
      <h2><span>{{ heading }}</span></h2><span
        v-if="dialog==='bond' && bondCharacter"
        class="modal-count"
      >{{ bondCharacter.stageName }}</span><span
        v-if="dialog==='all'||dialog==='works'"
        class="modal-count"
      >{{ t('meng.workCount',{count:works.data.total}) }}</span><span
        v-if="dialog==='memory'"
        class="modal-count"
      >{{ t('meng.memoryCount',{count:memory.data.length}) }}</span><button
        v-if="dialog==='bond'"
        type="button"
        class="meng-button pink reset-button"
        :disabled="busy"
        @click="reset"
      >
        {{ t('meng.reset') }}
      </button><button
        v-if="dialog==='memory' && memory.data.length"
        class="meng-button pink reset-button"
        type="button"
        :disabled="busy"
        @click="forgetAll"
      >
        {{ t('meng.forgetAll') }}
      </button>
    </template>
    <div class="modal-content">
      <div
        v-if="stageNotice"
        class="stage-up"
      >
        <img
          :src="avatar(modalCode)"
          alt=""
          width="84"
          height="84"
        ><h3>{{ t('meng.stageChange',{from:stageNotice.previousStage,to:stageNotice.stageName}) }}</h3><p class="stage-line">
          {{ stageNotice.message?.content }}
        </p><button
          class="meng-button primary"
          type="button"
          @click="close"
        >
          {{ t('meng.continue') }}
        </button>
      </div>
      <template v-else-if="dialog==='bond'">
        <MengState
          v-if="loadingBond"
          loading
        />
        <div
          v-else-if="bondCharacter"
          class="bond-content"
        >
          <div class="bond-summary">
            <img
              class="bond-avatar"
              :src="avatar(modalCode)"
              :alt="bondCharacter.name"
              width="100"
              height="100"
            ><div>
              <div class="bond-tags">
                <span class="stage-stamp">{{ t('meng.current',{stage:bondCharacter.stageName}) }}</span><span class="mint">{{ t('meng.days',{count:bondCharacter.acquaintanceDays}) }}</span><span class="violet">{{ t('meng.chatDays',{count:bondCharacter.chatDays}) }}</span><span class="pink">{{ t('meng.fileCount',{count:works.data.total}) }}</span>
              </div><p>{{ bondCharacter.lastMessage || t('meng.'+modalCode+'.greeting') }}</p>
            </div>
          </div>
          <section>
            <h3>{{ t('meng.path') }}<IconSparkles /></h3><div class="bond-road-scroll">
              <div class="bond-road">
                <img
                  :src="roadWaves"
                  alt=""
                  class="road-waves"
                  aria-hidden="true"
                >
                <img
                  :src="roadRainbow"
                  alt=""
                  class="road-rainbow"
                  aria-hidden="true"
                >
                <img
                  :src="roadSun"
                  alt=""
                  class="road-sun"
                  aria-hidden="true"
                >
                <div class="road-curve">
                  <span
                    class="road-active"
                    :style="{maskImage:'url('+roadProgress+')',clipPath:'inset(0 '+(90-progress(bondCharacter)*0.8)+'% 0 0)'}"
                    aria-hidden="true"
                  />
                  <img
                    :src="roadArt"
                    alt=""
                    class="road-line"
                    aria-hidden="true"
                  ><div
                    v-for="(stage,i) in stages"
                    :key="stage"
                    class="bond-road-stop"
                    :style="{'--stop':i,'--road-y':roadY[i]}"
                  >
                    <span
                      v-if="i===bondCharacter.stage-1"
                      class="current-avatar"
                    ><img
                      :src="avatar(modalCode)"
                      alt=""
                      width="40"
                      height="40"
                    ></span><span
                      class="road-dot"
                      :class="{current:i===bondCharacter.stage-1,locked:i>=bondCharacter.stage}"
                    ><IconCheck v-if="i<bondCharacter.stage-1" /><IconHeartFilled v-else-if="i===bondCharacter.stage-1" /><IconLock v-else /></span>
                  </div>
                </div>
                <ol>
                  <li
                    v-for="(stage,i) in stages"
                    :key="stage"
                    :class="{current:i===bondCharacter.stage-1,locked:i>=bondCharacter.stage}"
                  >
                    <strong>{{ stage }}<small v-if="i===bondCharacter.stage-1">{{ t('meng.now') }}</small></strong><span>{{ i < bondCharacter.stage ? t('meng.'+modalCode+'.descriptions.'+i):t('meng.future') }}</span><div
                      v-if="i===bondCharacter.stage-1"
                      class="stage-progress"
                    >
                      <span :style="{width:localProgress(bondCharacter)+'%'}" />
                    </div>
                  </li>
                </ol>
              </div>
            </div>
          </section>
          <div class="bond-links">
            <button
              class="meng-button pink"
              type="button"
              @click="dialog='memory'"
            >
              <IconHeart />{{ t('meng.memoryLink',{count:memory.data.length}) }}<IconChevronRight />
            </button><button
              class="meng-button violet"
              type="button"
              @click="dialog='works'"
            >
              <IconFiles />{{ t('meng.workLink',{count:works.data.total}) }}<IconChevronRight />
            </button>
          </div>
        </div>
      </template>
      <template v-else-if="dialog==='memory'">
        <MengState
          v-if="memory.loading||memory.error"
          :loading="memory.loading"
          :error="memory.error"
          @retry="loadMemory"
        />
        <MengState
          v-else-if="!memory.data.length"
          kind="memory"
          empty
        />
        <div
          v-else
          class="memory-groups"
        >
          <section
            v-for="group in memoryGroups"
            :key="group.kind"
          >
            <h3>{{ t('meng.memoryKind.'+group.kind) }}</h3>
            <ul class="memory-grid">
              <li
                v-for="(m,index) in group.records"
                :key="m.id"
                :style="{'--card-index':index}"
              >
                <span><IconSparkles /></span>
                <div class="memory-content">
                  <input
                    v-if="memoryEdits[m.id]"
                    v-model="memoryEdits[m.id].content"
                    maxlength="60"
                    :aria-label="t('meng.editMemory')"
                  >
                  <p v-else>
                    {{ m.content }}
                  </p>
                  <small v-if="m.dueDate">{{ t('meng.dueDate',{date:m.dueDate}) }}</small>
                  <div class="memory-actions">
                    <template v-if="memoryEdits[m.id]">
                      <button
                        type="button"
                        :disabled="busy || !memoryEdits[m.id].content.trim()"
                        :aria-label="t('meng.saveMemory')"
                        @click="editMemory(m,{content:memoryEdits[m.id].content})"
                      >
                        <IconCheck />
                      </button>
                      <button
                        type="button"
                        :aria-label="t('meng.cancelEdit')"
                        @click="delete memoryEdits[m.id]"
                      >
                        <IconX />
                      </button>
                    </template>
                    <button
                      v-else
                      type="button"
                      :aria-label="t('meng.editMemory')"
                      @click="memoryEdits[m.id]={content:m.content}"
                    >
                      <IconPencil />
                    </button>
                    <button
                      type="button"
                      :aria-label="t(m.pinned?'meng.unpinMemory':'meng.pinMemory')"
                      :aria-pressed="m.pinned"
                      :disabled="busy"
                      @click="editMemory(m,{pinned:!m.pinned})"
                    >
                      <IconPin />
                    </button>
                    <button
                      v-if="m.dueDate"
                      type="button"
                      :aria-label="t('meng.calendar')"
                      @click="calendar(m)"
                    >
                      <IconCalendar />
                    </button>
                    <button
                      type="button"
                      :aria-label="t('meng.forget')"
                      :disabled="busy"
                      @click="forget(m.id)"
                    >
                      <IconX />
                    </button>
                  </div>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </template>
      <template v-else>
        <div
          v-if="dialog==='all'"
          class="work-filters"
          role="group"
          :aria-label="t('meng.filter')"
        >
          <button
            type="button"
            class="meng-button"
            :class="{selected:!filter}"
            :aria-pressed="!filter"
            @click="setFilter('')"
          >
            {{ t('meng.all') }}
          </button><button
            v-for="c in characters.data"
            :key="c.code"
            type="button"
            class="meng-button"
            :class="[c.code.toLowerCase(),{selected:filter===c.code}]"
            :aria-pressed="filter===c.code"
            @click="setFilter(c.code)"
          >
            <img
              :src="avatar(c.code)"
              alt=""
              width="22"
              height="22"
            >{{ t('meng.'+c.code+'.short') }}
          </button>
        </div>
        <MengState
          v-if="works.loading||works.error"
          :loading="works.loading"
          :error="works.error"
          @retry="loadWorks"
        />
        <MengState
          v-else-if="!works.data.records.length"
          kind="works"
          empty
        />
        <div
          v-else
          class="work-body"
        >
          <section class="work-list">
            <button
              v-for="(w,index) in works.data.records"
              :key="w.id"
              type="button"
              class="work-card"
              :class="[w.characterCode.toLowerCase(),{selected:current?.id===w.id}]"
              :style="{'--card-index':index}"
              @click="selectWork(w.id)"
            >
              <span class="work-cover"><component :is="w.kind==='DOCUMENT'?IconFileText:IconPhoto" /><img
                :src="avatar(w.characterCode)"
                alt=""
                width="30"
                height="30"
              ></span><strong>{{ w.title }}</strong><span class="work-meta"><span>{{ t(w.kind==='DOCUMENT'?'meng.document':'meng.image') }}</span><small>{{ date(w.createTime) }}</small></span>
            </button><div
              v-if="works.data.total>Companion.PAGE_SIZE"
              class="work-pagination"
            >
              <button
                type="button"
                class="meng-button"
                :disabled="page===1"
                :aria-label="t('meng.previous')"
                @click="changePage(-1)"
              >
                <IconChevronLeft />
              </button><span>{{ page }}</span><button
                type="button"
                class="meng-button"
                :disabled="page*Companion.PAGE_SIZE>=works.data.total"
                :aria-label="t('meng.next')"
                @click="changePage(1)"
              >
                <IconChevronRight />
              </button>
            </div>
          </section>
          <aside
            class="work-preview"
            :class="current?.characterCode?.toLowerCase()"
          >
            <MengState
              v-if="preview.loading||preview.error"
              :loading="preview.loading"
              :error="preview.error"
              @retry="selectWork(selected)"
            /><template v-else-if="current">
              <header>
                <img
                  :src="avatar(current.characterCode)"
                  alt=""
                  width="36"
                  height="36"
                ><span>{{ t('meng.workBy',{name:t('meng.'+current.characterCode+'.short')}) }}</span><strong>{{ t('meng.preview') }}</strong>
              </header><h3>{{ current.title }}</h3><div
                v-if="current.kind==='DOCUMENT'"
                class="document-preview"
              >
                <MarkdownBody :content="current.body" />
              </div><div
                v-else
                class="image-preview"
              >
                <img
                  v-if="!imageError"
                  :key="current.id"
                  :src="fileUrl(current.id)"
                  :alt="current.title"
                  @error="imageError=true"
                ><button
                  v-else
                  type="button"
                  class="text-button"
                  @click="retryImage"
                >
                  {{ t('meng.imageFailed') }}
                </button>
              </div><div class="preview-actions">
                <button
                  v-if="current.kind==='DOCUMENT'"
                  class="meng-button secondary"
                  type="button"
                  @click="revise(current)"
                >
                  {{ t('meng.revise') }}
                </button>
                <button
                  class="meng-button violet"
                  type="button"
                  :disabled="busy"
                  @click="toDraft"
                >
                  {{ t('meng.draftShort') }}
                </button><a
                  class="meng-button mint"
                  :href="fileUrl(current.id)"
                  :download="current.title+(current.kind==='DOCUMENT'?'.md':'.png')"
                >{{ t('meng.download') }}</a><button
                  class="meng-button pink"
                  type="button"
                  :aria-label="t('meng.delete')"
                  :disabled="busy"
                  @click="deleteWork"
                >
                  <IconTrash />
                </button>
              </div>
            </template>
          </aside>
        </div>
      </template>
    </div>
  </DialogShell>
</template>
<script src="./MengDialogs.js"></script>
<style lang="scss" scoped src="./MengDialogs.scss"></style>
