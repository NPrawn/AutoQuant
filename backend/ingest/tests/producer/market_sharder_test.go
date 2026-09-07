package producer_test

import (
	"reflect"
	"testing"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
)

func TestShardForMarket(t *testing.T) {
	tests := []struct {
		name       string
		marketCode string
		want       int32
	}{
		{name: "비트코인 샤드를 계산한다", marketCode: "KRW-BTC", want: 3},
		{name: "이더리움 샤드를 계산한다", marketCode: "KRW-ETH", want: 1},
		{name: "리플 샤드를 계산한다", marketCode: "KRW-XRP", want: 4},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := producer.ShardForMarket(test.marketCode); got != test.want {
				t.Fatalf("ShardForMarket(%q) = %d, want %d", test.marketCode, got, test.want)
			}
		})
	}
}

func TestTargetsForShards(t *testing.T) {
	bitcoin := assetdomain.MarketDataCollectionTarget{AssetID: "asset-1", MarketCode: "KRW-BTC"}
	ethereum := assetdomain.MarketDataCollectionTarget{AssetID: "asset-2", MarketCode: "KRW-ETH"}
	ripple := assetdomain.MarketDataCollectionTarget{AssetID: "asset-3", MarketCode: "KRW-XRP"}
	targets := []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum, ripple}

	tests := []struct {
		name   string
		shards []int32
		want   []assetdomain.MarketDataCollectionTarget
	}{
		{name: "소유 샤드의 종목만 반환한다", shards: []int32{1, 4}, want: []assetdomain.MarketDataCollectionTarget{ethereum, ripple}},
		{name: "소유 샤드가 없으면 빈 목록을 반환한다", want: []assetdomain.MarketDataCollectionTarget{}},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got := producer.TargetsForShards(targets, test.shards)
			if !reflect.DeepEqual(got, test.want) {
				t.Fatalf("TargetsForShards() = %#v, want %#v", got, test.want)
			}
		})
	}
}
