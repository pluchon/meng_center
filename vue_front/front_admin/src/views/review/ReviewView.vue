<template>
  <main class="review-layout">
    <header>
      <div>
        <h1>{{ t('user.review.title') }}</h1>
        <p>{{ t('user.review.description') }}</p>
      </div>
      <el-button @click="logout">
        {{ t('common.logout') }}
      </el-button>
    </header>
    <section class="review-card">
      <el-select
        v-model="status"
        :placeholder="t('user.review.all')"
        clearable
        @change="filter"
      >
        <el-option
          v-for="value in UserStatus"
          :key="value"
          :value="value"
          :label="t('user.status.' + value)"
        />
      </el-select>
      <PageState
        v-if="!user.loggedIn"
        state="unauthenticated"
      />
      <PageState
        v-else-if="loading"
        state="loading"
      />
      <PageState
        v-else-if="error"
        state="error"
        @retry="load"
      />
      <PageState
        v-else-if="!data?.records?.length"
        state="empty"
      />
      <el-table
        v-else
        :data="data.records"
        class="review-table"
      >
        <el-table-column
          prop="username"
          :label="t('user.review.username')"
          min-width="130"
        />
        <el-table-column
          :label="t('user.review.contact')"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.email || row.phone }}
          </template>
        </el-table-column>
        <el-table-column
          prop="applyReason"
          :label="t('user.review.reason')"
          min-width="220"
        />
        <el-table-column
          prop="createTime"
          :label="t('user.review.time')"
          min-width="190"
        />
        <el-table-column
          :label="t('user.review.status')"
          min-width="100"
        >
          <template #default="{ row }">
            {{ t('user.status.' + row.status) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('user.review.actions')"
          min-width="170"
        >
          <template #default="{ row }">
            <template v-if="row.status === UserStatus.PENDING">
              <el-button
                type="primary"
                :disabled="busy"
                @click="approve(row.id)"
              >
                {{ t('user.review.approve') }}
              </el-button><el-button
                type="danger"
                :disabled="busy"
                @click="reject(row.id)"
              >
                {{ t('user.review.reject') }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <p
        v-if="failure"
        role="alert"
      >
        {{ failure }}
      </p>
      <el-pagination
        v-if="data?.total"
        v-model:current-page="page"
        :page-size="Paging.SIZE"
        :total="data.total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </section>
  </main>
</template>
<script src="./ReviewView.js"></script>
<style lang="scss" scoped src="./ReviewView.scss"></style>
