<template>
  <el-dialog
    :model-value="open"
    :title="t('ai.'+mode)"
    width="min(660px,calc(100vw - 32px))"
    class="ai-assistant-dialog"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:open',$event)"
  >
    <template #header>
      <div class="ai-heading">
        <img
          :src="editorMagicIcon"
          alt=""
        ><h2>{{ t('ai.'+mode) }}</h2>
        <small class="ai-credit"><img
          :src="editorMagicIcon"
          alt=""
          aria-hidden="true"
        >{{ t('ai.generated') }}</small>
        <div
          v-if="mode!=='inspirations'"
          class="ai-period"
          role="group"
          :aria-label="t('ai.'+mode)"
        >
          <span
            :style="{'--period':days===30?1:0}"
            aria-hidden="true"
          />
          <button
            v-for="period in [7,30]"
            :key="period"
            type="button"
            :aria-pressed="days===period"
            @click="days=period"
          >
            {{ t('ai.days',{days:period}) }}
          </button>
        </div>
        <button
          type="button"
          class="ai-close"
          :aria-label="t('ai.close')"
          @click="$emit('update:open',false)"
        >
          <img
            :src="messageCloseIcon"
            alt=""
          >
        </button>
      </div>
    </template>
    <div
      class="ai-content"
      :aria-busy="resource.loading"
    >
      <PageState
        v-if="resource.error"
        state="error"
        :text="resource.error.message"
        @retry="load"
      />
      <div
        v-else-if="mode==='inspirations'&&!resource.data"
        class="ai-inspirations"
        role="status"
        :aria-label="t('ai.loading')"
      >
        <article
          v-for="index in 3"
          :key="index"
          class="ai-card ai-glowing"
        >
          <GlowRing />
          <div class="ai-card-head">
            <b class="ai-card-no">{{ index }}</b>
            <h3 v-if="index===1">
              {{ t('ai.loading') }}
            </h3>
            <i
              v-else
              class="ai-bar ai-bar-title"
            />
          </div>
          <div class="ai-lines">
            <i class="ai-bar" />
            <i class="ai-bar ai-bar-short" />
          </div>
          <i class="ai-bar ai-bar-block" />
        </article>
      </div>
      <div
        v-else-if="!resource.data"
        class="ai-analysis"
        role="status"
        :aria-label="t('ai.loading')"
      >
        <div class="ai-metrics">
          <span
            v-for="index in 3"
            :key="index"
            class="ai-glowing"
          >
            <GlowRing />
            <i class="ai-bar ai-bar-title" />
            <i class="ai-bar ai-bar-short" />
          </span>
        </div>
        <div class="ai-note ai-glowing">
          <GlowRing />
          <div class="ai-summary">
            <i class="ai-bar" />
            <i class="ai-bar" />
            <i class="ai-bar ai-bar-short" />
          </div>
        </div>
        <section class="ai-suggest ai-glowing">
          <GlowRing />
          <h3>{{ t('ai.loading') }}</h3>
          <i class="ai-bar" />
          <i class="ai-bar ai-bar-short" />
        </section>
      </div>
      <Transition
        name="ai-result"
        mode="out-in"
      >
        <div
          v-if="resource.data&&!resource.error"
          :key="revision"
          :class="{'ai-refreshing':resource.loading}"
        >
          <div
            v-if="mode==='inspirations'"
            class="ai-inspirations"
          >
            <article
              v-for="(item,index) in resource.data.inspirations"
              :key="item.angle"
              class="ai-card"
            >
              <div class="ai-card-head">
                <b class="ai-card-no">{{ index+1 }}</b>
                <h3 :title="item.angle">
                  {{ item.angle }}
                </h3>
                <button
                  type="button"
                  :aria-label="t('ai.pickLabel',{angle:item.angle})"
                  :disabled="resource.loading"
                  @click="insert(item.opening)"
                >
                  {{ t('ai.pick') }}
                </button>
              </div>
              <p>{{ item.explanation }}</p>
              <blockquote>{{ item.opening }}</blockquote>
            </article>
          </div>
          <div
            v-else
            class="ai-analysis"
          >
            <div class="ai-metrics">
              <span
                v-for="metric in metrics"
                :key="metric.label"
              ><img
                class="ai-metric-icon"
                :src="metric.icon"
                alt=""
                aria-hidden="true"
              ><small>{{ t('ai.'+metric.label) }}</small><strong>{{ metric.value }}</strong></span>
            </div>
            <div class="ai-note">
              <p class="ai-summary">
                {{ resource.data.result.summary }}
              </p>
            </div>
            <section class="ai-suggest">
              <h3>{{ t('ai.suggestions') }}</h3>
              <ul>
                <li
                  v-for="suggestion in resource.data.result.suggestions"
                  :key="suggestion"
                >
                  {{ suggestion }}
                </li>
              </ul>
            </section>
          </div>
        </div>
      </Transition>
    </div>
  </el-dialog>
</template>
<script src="./AiAssistant.js"></script>
<style lang="scss" src="./AiAssistant.scss"></style>
